package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.ArrayType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.ConstantPoolEntryClass;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:44
 * To change this template use File | Settings | File Templates.
 */
public class NewObjectArray implements Expression {
    private Expression size;
    private final ConstantPool cp;
    private final ConstantPoolEntryClass type;

    public NewObjectArray(Expression size, ConstantPool constantPool, ConstantPoolEntry type) {
        this.size = size;
        this.cp = constantPool;
        this.type = (ConstantPoolEntryClass) type;
    }

    @Override
    public String toString() {
        String name = cp.getUTF8Entry(type.getNameIndex()).getValue();
        return "new " + name + "[" + size + "]";
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueCollector lValueCollector, SSAIdentifiers ssaIdentifiers) {
        size = size.replaceSingleUsageLValues(lValueCollector, ssaIdentifiers);
        return this;
    }

}
