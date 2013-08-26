package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.constantpool.*;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 * Time: 18:32
 * To change this template use File | Settings | File Templates.
 */
public class StaticVariable extends AbstractLValue {

    private final ConstantPoolEntryFieldRef field;
    private final JavaTypeInstance clazz;
    private final String varName;

    public StaticVariable(ClassFile classFile, ConstantPool cp, ConstantPoolEntry field) {
        super(FieldVariable.getFieldType((ConstantPoolEntryFieldRef) field));
        this.field = (ConstantPoolEntryFieldRef) field;
        this.clazz = this.field.getClassEntry().getTypeInstance();
        this.varName = this.field.getLocalName();
    }

    /*
     * Used only for matching
     */
    public StaticVariable(InferredJavaType type, JavaTypeInstance clazz, String varName) {
        super(type);
        this.field = null;
        this.varName = varName;
        this.clazz = clazz;
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }

    public JavaTypeInstance getOwningClassTypeInstance() {
        return clazz;
    }

    public ConstantPoolEntryNameAndType getNameAndTypeEntry() {
        return field.getNameAndTypeEntry();
    }

    public ConstantPoolEntryClass getClassEntry() {
        return field.getClassEntry();
    }

    public String getVarName() {
        return varName;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print(clazz.toString() + "." + varName);
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
        if (!other.clazz.equals(clazz)) return false;
        return other.varName.equals(varName);
    }

    @Override
    public int hashCode() {
        int hashcode = clazz.hashCode();
        hashcode = (13 * hashcode) + varName.hashCode();
        return hashcode;
    }
}
