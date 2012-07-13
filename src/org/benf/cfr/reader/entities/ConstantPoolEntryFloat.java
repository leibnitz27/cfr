package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.StackType;
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
    private final float value;

    public ConstantPoolEntryFloat(ByteData data) {
        this.value = data.getFloatAt(1);
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

    public float getValue() {
        return value;
    }
}
