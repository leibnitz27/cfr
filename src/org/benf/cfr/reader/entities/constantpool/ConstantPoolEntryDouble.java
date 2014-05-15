package org.benf.cfr.reader.entities.constantpool;

import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.entities.AbstractConstantPoolEntry;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

public class ConstantPoolEntryDouble extends AbstractConstantPoolEntry implements ConstantPoolEntryLiteral {
    private final double value;

    public ConstantPoolEntryDouble(ConstantPool cp, ByteData data) {
        super(cp);
        this.value = data.getDoubleAt(1);
    }

    @Override
    public long getRawByteLength() {
        return 9;
    }

    @Override
    public void dump(Dumper d) {
        d.print("CONSTANT_Double " + value);
    }

    public double getValue() {
        return value;
    }

    @Override
    public StackType getStackType() {
        return StackType.DOUBLE;
    }
}
