package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 20:38
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolEntryLong extends AbstractConstantPoolEntry implements ConstantPoolEntryLiteral {
    private final long value;

    public ConstantPoolEntryLong(ConstantPool cp, ByteData data) {
        super(cp);
        this.value = data.getLongAt(1);
    }

    @Override
    public long getRawByteLength() {
        return 9;
    }

    @Override
    public void dump(Dumper d) {
        d.print("CONSTANT_Long " + value);
    }

    @Override
    public String toString() {
        return "CONSTANT_Long[" + value + "]";
    }

    public long getValue() {
        return value;
    }

    @Override
    public StackType getStackType() {
        return StackType.LONG;
    }
}
