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
public class ConstantPoolEntryClass implements ConstantPoolEntry {
    private final long OFFSET_OF_NAME_INDEX = 1;

    final short nameIndex;

    public ConstantPoolEntryClass(ByteData data)
    {
        this.nameIndex = data.getU2At(OFFSET_OF_NAME_INDEX);
    }

    @Override
    public long getRawByteLength()
    {
        return 3;
    }

    @Override
    public String toString()
    {
        return "CONSTANT_Class " + nameIndex;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp)
    {
        d.print("Class " + cp.getUTF8Entry(nameIndex).getValue());
    }

    public short getNameIndex() {
        return nameIndex;
    }
}
