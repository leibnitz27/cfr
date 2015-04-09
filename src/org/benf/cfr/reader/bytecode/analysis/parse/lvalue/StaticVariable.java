package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.constantpool.*;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

public class StaticVariable extends AbstractLValue {

    private final ClassFileField classFileField;
    private final String failureName;
    private final boolean knownSimple;
    private final JavaTypeInstance owningClass;

    public StaticVariable(ConstantPoolEntry field) {
        super(FieldVariable.getFieldType((ConstantPoolEntryFieldRef) field));
        ConstantPoolEntryFieldRef fieldRef = (ConstantPoolEntryFieldRef) field;
        this.classFileField = FieldVariable.getField(fieldRef);
        this.failureName = fieldRef.getLocalName();
        this.knownSimple = false;
        this.owningClass = fieldRef.getClassEntry().getTypeInstance();
    }

    /*
     * Used only for matching
     */
    public StaticVariable(InferredJavaType type, JavaTypeInstance clazz, String varName) {
        super(type);
        this.classFileField = null;
        this.failureName = varName;
        this.knownSimple = false;
        this.owningClass = clazz;
    }

    private StaticVariable(StaticVariable other, boolean knownSimple) {
        super(other.getInferredJavaType());
        this.classFileField = other.classFileField;
        this.failureName = other.failureName;
        this.knownSimple = knownSimple;
        this.owningClass = other.owningClass;
    }

    /*
     * There are some circumstances (final assignment) where it's illegal to use the FQN of a static.
     */
    public StaticVariable getSimpleCopy() {
        return new StaticVariable(this, true);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        if (classFileField != null) collector.collect(classFileField.getField().getJavaTypeInstance());
        collector.collect(owningClass);
        super.collectTypeUsages(collector);
    }

    @Override
    public void markFinal() {

    }

    @Override
    public boolean isFinal() {
        return false;
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }

    public JavaTypeInstance getOwningClassTypeInstance() {
        return owningClass;
    }

    public String getFieldName() {
        if (classFileField == null) {
            return failureName;
        }
        return classFileField.getFieldName();
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.HIGHEST;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        if (knownSimple) {
            return d.identifier(getFieldName());
        } else {
            return d.dump(owningClass).print(".").identifier(getFieldName());
        }
    }

    @Override
    public LValue deepClone(CloneHelper cloneHelper) {
        return this;
    }

    @Override
    public void collectLValueAssignments(Expression assignedTo, StatementContainer statementContainer, LValueAssignmentCollector lValueAssigmentCollector) {
    }

    @Override
    public LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        return this;
    }

    @Override
    public LValue applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }

    @Override
    public SSAIdentifiers<LValue> collectVariableMutation(SSAIdentifierFactory<LValue> ssaIdentifierFactory) {
        return new SSAIdentifiers(this, ssaIdentifierFactory);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StaticVariable)) return false;
        StaticVariable other = (StaticVariable) o;
        if (!other.owningClass.equals(owningClass)) return false;
        return other.getFieldName().equals(getFieldName());
    }

    @Override
    public int hashCode() {
        int hashcode = owningClass.hashCode();
        hashcode = (13 * hashcode) + getFieldName().hashCode();
        return hashcode;
    }
}
