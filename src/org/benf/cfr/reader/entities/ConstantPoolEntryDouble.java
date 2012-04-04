package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 20:38
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolEntryDouble implements ConstantPoolEntry {
    private final double value;

    public ConstantPoolEntryDouble(ByteData data) {
        this.value = data.getDoubleAt(1);
    }

    @Override
    public long getRawByteLength()
    {
        return 9;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp)
    {
        d.print("CONSTANT_Double " + value);
    }

    public double getValue() {
        return value;
    }
}
