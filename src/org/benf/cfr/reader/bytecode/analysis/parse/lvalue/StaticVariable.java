package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
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
public class StaticVariable implements LValue {

    private final ConstantPool cp;
    private final ConstantPoolEntryFieldRef field;

    public StaticVariable(ConstantPool cp, ConstantPoolEntry field) {
        this.field = (ConstantPoolEntryFieldRef)field;
        this.cp = cp;
    }

    @Override
    public int getNumberOfCreators() {
        throw new ConfusedCFRException("NYI");
    }


    @Override
    public String toString() {
        String className = cp.getUTF8Entry(cp.getClassEntry(field.getClassIndex()).getNameIndex()).getValue();
        return className + "." + field.getLocalName(cp);
    }

    @Override
    public void determineLValueEquivalence(Expression assignedTo, StatementContainer statementContainer, LValueCollector lValueCollector) {
    }

    public LValue replaceSingleUsageLValues(LValueCollector lValueCollector) {
        return this;
    }
}
