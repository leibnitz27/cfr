package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 20:36
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolEntryString extends AbstractConstantPoolEntry implements ConstantPoolEntryLiteral {
    private final long OFFSET_OF_STRING_INDEX = 1;

    private final long stringIndex;

    public ConstantPoolEntryString(ConstantPool cp, ByteData data) {
        super(cp);
        this.stringIndex = data.getS2At(OFFSET_OF_STRING_INDEX);
    }

    @Override
    public long getRawByteLength() {
        return 3;
    }

    @Override
    public void dump(Dumper d) {
        ConstantPool cp = getCp();
        d.print("String " + cp.getUTF8Entry((int) stringIndex).getValue());
    }

    public String getValue() {
        return getCp().getUTF8Entry((int) stringIndex).getValue();
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }
}


