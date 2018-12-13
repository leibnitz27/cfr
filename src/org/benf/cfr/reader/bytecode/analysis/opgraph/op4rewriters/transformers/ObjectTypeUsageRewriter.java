package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.AnonymousClassUsage;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.BindingSuperContainer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.Map;

/**
 * Detect potential invalid usages of fields - where the class we expect to be calling them with doesn't
 * match the class needed to access the fields in question.
 */
public class ObjectTypeUsageRewriter extends AbstractExpressionRewriter implements StructuredStatementTransformer {

    private final Map<InferredJavaType, Boolean> isAnonVar = MapFactory.newIdentityMap();
    private boolean canHaveVar;

    public ObjectTypeUsageRewriter(AnonymousClassUsage anonymousClassUsage, ClassFile classFile) {
        this.canHaveVar = !anonymousClassUsage.isEmpty() && classFile.getClassFileVersion().equalOrLater(ClassFileVersion.JAVA_10);
    }

    public void transform(Op04StructuredStatement root) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        in.transformStructuredChildren(this, scope);
        in.rewriteExpressions(this);
        return in;
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof MemberFunctionInvokation) {
            expression = handleMemberFunction((MemberFunctionInvokation)expression);
        }
        return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (lValue instanceof FieldVariable) {
            lValue = handleFieldVariable((FieldVariable)lValue);
        }

        return super.rewriteExpression(lValue, ssaIdentifiers, statementContainer, flags);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean needsReWrite(Expression lhsObject, JavaTypeInstance owningClassType, UnaryFunction<ClassFile, Boolean> checkVisible) {
        if (owningClassType == null) {
            return false;
        }
        InferredJavaType ijtObject = lhsObject.getInferredJavaType();
        if (canHaveVar) {
            if (!isAnonVar.containsKey(ijtObject)) {
                boolean isAnon = owningClassType.getInnerClassHereInfo().isAnonymousClass();
                if (isAnon) {
                    ijtObject.confirmVarIfPossible();
                    markLocalVar(lhsObject);
                }
                isAnonVar.put(ijtObject, isAnon);
            }
        }

        /*
         * Ok - and what if the property being referenced is not part of the detected class?
         */
        JavaTypeInstance jtObj = ijtObject.getJavaTypeInstance();
        if (owningClassType.getInnerClassHereInfo().isAnonymousClass() || jtObj == owningClassType) {
            return false;
        }

        owningClassType = owningClassType.getDeGenerifiedType();
        if (owningClassType instanceof JavaRefTypeInstance) {
            ClassFile owningClassFile = (((JavaRefTypeInstance) owningClassType).getClassFile());
            if (owningClassFile != null && owningClassFile.isInterface()) return false;
        }

        JavaTypeInstance currentAsWas = jtObj.getDeGenerifiedType();
        /*
         * IF there's no route at all between the "known" type and the invokation type,
         * then we've probably detected a too-super type for the known type.
         *
         * This can happen when two objects share a slot, and we're not able to split the
         * lifetime correctly.
         */
        BindingSuperContainer bindingSupers = currentAsWas.getBindingSupers();
        if (bindingSupers != null) {
            if (!bindingSupers.containsBase(owningClassType)) {
                return true;
            }
        }

        /*
         * This is fine, IF there's no route between jtObj and jtField in which there *IS* a property which would
         * override
         */
        do {
            if (currentAsWas.equals(owningClassType)) return false;
            JavaTypeInstance current = currentAsWas;
            if (!(current instanceof JavaRefTypeInstance)) return false;
            ClassFile classFile = ((JavaRefTypeInstance) current).getClassFile();
            if (classFile == null) {
                return false;
            }
            if (checkVisible.invoke(classFile)) {
                break;
            }
            currentAsWas = classFile.getBaseClassType().getDeGenerifiedType();
        } while (currentAsWas != null);
        if (currentAsWas == null || currentAsWas.equals(owningClassType)) {
            return false;
        }
        return true;
    }

    private Expression handleMemberFunction(final MemberFunctionInvokation funcInv) {

        class MemberCheck implements UnaryFunction<ClassFile, Boolean> {
            @Override
            public Boolean invoke(ClassFile classFile) {
                return classFile.getMethodByPrototypeOrNull(funcInv.getMethodPrototype()) != null;
            }
        }

        Expression lhsObject = funcInv.getObject();
        JavaTypeInstance owningClassType = funcInv.getMethodPrototype().getClassType();
        if (!needsReWrite(lhsObject, owningClassType, new MemberCheck())) return funcInv;
        return funcInv.withReplacedObject(new CastExpression(new InferredJavaType(owningClassType, InferredJavaType.Source.FORCE_TARGET_TYPE), lhsObject));
    }

    private LValue handleFieldVariable(final FieldVariable fieldVariable) {

        class FieldCheck implements UnaryFunction<ClassFile, Boolean> {
            @Override
            public Boolean invoke(ClassFile classFile) {
                return classFile.hasField(fieldVariable.getFieldName());
            }
        }

        Expression lhsObject = fieldVariable.getObject();
        JavaTypeInstance owningClassType = fieldVariable.getOwningClassType();
        if (!needsReWrite(lhsObject, owningClassType, new FieldCheck())) return fieldVariable;

        return fieldVariable.withReplacedObject(new CastExpression(new InferredJavaType(owningClassType, InferredJavaType.Source.FORCE_TARGET_TYPE), lhsObject));
    }

    private void markLocalVar(Expression object) {
        if (!(object instanceof LValueExpression)) return;
        LValue lValue = ((LValueExpression) object).getLValue();
        lValue.markVar();
    }

}
