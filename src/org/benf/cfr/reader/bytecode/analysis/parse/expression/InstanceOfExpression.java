package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.ConstantPoolEntryClass;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:51
 * To change this template use File | Settings | File Templates.
 */
public class InstanceOfExpression implements Expression {
    private Expression lhs;
    private String className;

    public InstanceOfExpression(Expression lhs, ConstantPool cp, ConstantPoolEntry cpe) {
        this.lhs = lhs;
        ConstantPoolEntryClass cpec = (ConstantPoolEntryClass) cpe;
        this.className = cp.getUTF8Entry(cpec.getNameIndex()).getValue();
    }

    @Override
    public String toString() {
        return "(" + lhs.toString() + " instanceof " + className + ")";
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueCollector lValueCollector, SSAIdentifiers ssaIdentifiers) {
        lhs = lhs.replaceSingleUsageLValues(lValueCollector, ssaIdentifiers);
        return this;
    }
}
