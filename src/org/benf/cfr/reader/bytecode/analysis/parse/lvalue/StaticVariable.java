package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.ConstantPoolEntryFieldRef;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 * Time: 18:32
 * To change this template use File | Settings | File Templates.
 */
public class StaticVariable extends AbstractLValue {

    private final ConstantPool cp;
    private final ConstantPoolEntryFieldRef field;
    private final String className;
    private final String varName;

    public StaticVariable(ConstantPool cp, ConstantPoolEntry field) {
        super(new InferredJavaType(((ConstantPoolEntryFieldRef) field).getJavaTypeInstance(cp), InferredJavaType.Source.FIELD));
        this.field = (ConstantPoolEntryFieldRef) field;
        this.cp = cp;
        this.className = cp.getUTF8Entry(cp.getClassEntry(this.field.getClassIndex()).getNameIndex()).getValue();
        cp.markClassNameUsed(className);
        this.varName = this.field.getLocalName(cp);
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }


    @Override
    public String toString() {
        return cp.getDisplayableClassName(className) + "." + varName;
    }

    @Override
    public void determineLValueEquivalence(Expression assignedTo, StatementContainer statementContainer, LValueAssignmentCollector lValueAssigmentCollector) {
    }

    @Override
    public LValue replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        return this;
    }

    @Override
    public SSAIdentifiers collectVariableMutation(SSAIdentifierFactory ssaIdentifierFactory) {
        return new SSAIdentifiers(this, ssaIdentifierFactory);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StaticVariable)) return false;
        StaticVariable other = (StaticVariable) o;
        if (!other.className.equals(className)) return false;
        return other.varName.equals(varName);
    }

    @Override
    public int hashCode() {
        int hashcode = className.hashCode();
        hashcode = (13 * hashcode) + varName.hashCode();
        return hashcode;
    }
}
