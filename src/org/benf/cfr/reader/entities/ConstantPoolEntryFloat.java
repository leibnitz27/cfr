package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.stack.StackType;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 20:35
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolEntryFloat implements ConstantPoolEntry, ConstantPoolEntryLiteral {
    public ConstantPoolEntryFloat(ByteData data) {
    }

    @Override
    public long getRawByteLength() {
        return 5;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp) {
        d.print("CONSTANT_Float");
    }

    @Override
    public StackType getStackType() {
        return StackType.FLOAT;
    }
}
