package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.AnonymousClassUsage;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Detect potential invalid usages of fields - where the class we expect to be calling them with doesn't
 * match the class needed to access the fields in question.S
 */
public class FieldTypeUsageRewriter extends AbstractExpressionRewriter implements StructuredStatementTransformer {

    private final Map<InferredJavaType, Boolean> isAnonVar = MapFactory.newIdentityMap();
    private boolean canHaveVar;

    public FieldTypeUsageRewriter(AnonymousClassUsage anonymousClassUsage, ClassFile classFile) {
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
        return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        fv : if (lValue instanceof FieldVariable) {
            FieldVariable fieldVariable = (FieldVariable) lValue;
            Expression fieldVariableObject = fieldVariable.getObject();
            InferredJavaType ijtObject = fieldVariableObject.getInferredJavaType();
            JavaTypeInstance owningClassType = fieldVariable.getOwningClassType();
            if (canHaveVar) {
                if (!isAnonVar.containsKey(ijtObject)) {
                    boolean isAnon = owningClassType.getInnerClassHereInfo().isAnonymousClass();
                    if (isAnon) {
                        ijtObject.confirmVarIfPossible();
                        markLocalVar(((FieldVariable) lValue).getObject());
                    }
                    isAnonVar.put(ijtObject, isAnon);
                }
            }
            /*
             * Ok - and what if the field being referenced is not part of the detected class?
             */
            JavaTypeInstance jtObj = ijtObject.getJavaTypeInstance();
            if (owningClassType.getInnerClassHereInfo().isAnonymousClass() || jtObj == owningClassType) {
                break fv;
            }

            /*
             * This is fine, IF there's no route between jtObj and jtField in which there *IS* a member which would
             * override
             */
            JavaTypeInstance currentAsWas = jtObj;
            do {
                if (currentAsWas.equals(owningClassType)) break fv;
                JavaTypeInstance current = currentAsWas.getDeGenerifiedType();
                if (!(current instanceof JavaRefTypeInstance)) break fv;
                ClassFile classFile = ((JavaRefTypeInstance) current).getClassFile();
                if (classFile.hasField(fieldVariable.getFieldName())) {
                    break;
                }
                currentAsWas = classFile.getBaseClassType();
            } while (currentAsWas != null);
            if (currentAsWas == null) {
                break fv;
            }

            Map<Expression, Expression> replace = MapFactory.newIdentityMap();
            replace.put(fieldVariableObject, new CastExpression(new InferredJavaType(owningClassType, InferredJavaType.Source.FORCE_TARGET_TYPE), fieldVariableObject));
            lValue = fieldVariable.deepClone(new CloneHelper(replace));
        }

        return super.rewriteExpression(lValue, ssaIdentifiers, statementContainer, flags);
    }

    private void markLocalVar(Expression object) {
        if (!(object instanceof LValueExpression)) return;
        LValue lValue = ((LValueExpression) object).getLValue();
        lValue.markVar();
    }

}
