package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryFieldRef;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 * Time: 18:32
 * To change this template use File | Settings | File Templates.
 * <p/>
 * Note - a field variable LValue means an lValue of ANY object.
 */
public class FieldVariable extends AbstractLValue {

    private Expression object;

    private final ClassFile classFile;
    private final ClassFileField classFileField;
    private final String failureName; // if we can't get the classfileField.

    public FieldVariable(Expression object, ClassFile classFile, ConstantPoolEntry field) {
        super(getFieldType((ConstantPoolEntryFieldRef) field));
        this.classFile = classFile;
        this.object = object;
        ConstantPoolEntryFieldRef fieldRef = (ConstantPoolEntryFieldRef) field;
        this.classFileField = getField(fieldRef);
        this.failureName = fieldRef.getLocalName();
    }

    private FieldVariable(InferredJavaType type, Expression object, ClassFile classFile, ClassFileField classFileField, String failureName) {
        super(type);
        this.object = object;
        this.classFile = classFile;
        this.classFileField = classFileField;
        this.failureName = failureName;
    }

    @Override
    public LValue deepClone(CloneHelper cloneHelper) {
        return new FieldVariable(getInferredJavaType(), cloneHelper.replaceOrClone(object), classFile, classFileField, failureName);
    }

    static ClassFileField getField(ConstantPoolEntryFieldRef fieldRef) {
        String name = fieldRef.getLocalName();
        JavaRefTypeInstance ref = (JavaRefTypeInstance) fieldRef.getClassEntry().getTypeInstance();
        try {
            ClassFile classFile = ref.getClassFile();
            if (classFile == null) return null;

            ClassFileField field = classFile.getFieldByName(name);
            return field;
        } catch (NoSuchFieldException ignore) {
        } catch (CannotLoadClassException ignore) {
        }
        return null;
    }

    static InferredJavaType getFieldType(ConstantPoolEntryFieldRef fieldRef) {
        String name = fieldRef.getLocalName();
        JavaRefTypeInstance ref = (JavaRefTypeInstance) fieldRef.getClassEntry().getTypeInstance();
        try {
            ClassFile classFile = ref.getClassFile();
            if (classFile != null) {
                Field field = classFile.getFieldByName(name).getField();
                return new InferredJavaType(field.getJavaTypeInstance(classFile.getConstantPool()), InferredJavaType.Source.FIELD);
            }
        } catch (CannotLoadClassException e) {
        } catch (NoSuchFieldException ignore) {
        }
        return new InferredJavaType(fieldRef.getJavaTypeInstance(), InferredJavaType.Source.FIELD);
    }

    public ClassFileField getClassFileField() {
        return classFileField;
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }

    public JavaTypeInstance getOwningClassType() {
        return classFile.getClassType();
    }

    /*
     * This will only be meaningful after the inner class constructor transformation.
     */
    public boolean isOuterRef() {
        return classFileField != null && classFileField.isSyntheticOuterRef();
    }

    public String getFieldName() {
        if (classFileField == null) {
            return failureName;
        }
        return classFileField.getFieldName();
    }

    public Expression getObject() {
        return object;
    }

    private boolean objectIsThis() {
        if (object instanceof LValueExpression) {
            LValue lValue = ((LValueExpression) object).getLValue();
            if (lValue instanceof LocalVariable) {
                return ((LocalVariable) lValue).getName().getStringName().equals(MiscConstants.THIS);
            }
        }
        return false;
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        if (isOuterRef() && objectIsThis()) {
            return d.print(getFieldName());
        } else {
            object.dumpWithOuterPrecedence(d, getPrecedence());
            return d.print(".").print(getFieldName());
        }
    }

    @Override
    public SSAIdentifiers<LValue> collectVariableMutation(SSAIdentifierFactory<LValue> ssaIdentifierFactory) {
        return new SSAIdentifiers(this, ssaIdentifierFactory);
    }

    @Override
    public void collectLValueAssignments(Expression assignedTo, StatementContainer statementContainer, LValueAssignmentCollector lValueAssigmentCollector) {
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        object.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        object = object.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public LValue applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        object = expressionRewriter.rewriteExpression(object, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    public void rewriteLeftNestedSyntheticOuterRefs() {
        if (isOuterRef()) {
            while (object instanceof LValueExpression) {
                LValue lValueLhs = ((LValueExpression) object).getLValue();
                if (lValueLhs instanceof FieldVariable) {
                    FieldVariable lhs = (FieldVariable) lValueLhs;
                    if (lhs.isOuterRef()) {
                        object = lhs.object;
                        continue;
                    }
                }
                break;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;

        if (!(o instanceof FieldVariable)) return false;
        FieldVariable other = (FieldVariable) o;

        if (!object.equals(other.object)) return false;
        if (!getFieldName().equals(other.getFieldName())) return false;
        return true;
    }
}
