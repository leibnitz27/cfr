package org.benf.cfr.reader.entities.constantpool;

import org.benf.cfr.reader.entities.AbstractConstantPoolEntry;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

public class ConstantPoolEntryInvokeDynamic extends AbstractConstantPoolEntry {
    private static final long OFFSET_OF_BOOTSTRAP_METHOD_ATTR_INDEX = 1;
    private static final long OFFSET_OF_NAME_AND_TYPE_INDEX = 3;

    private final int bootstrapMethodAttrIndex;
    private final int nameAndTypeIndex;

    public ConstantPoolEntryInvokeDynamic(ConstantPool cp, ByteData data) {
        super(cp);
        this.bootstrapMethodAttrIndex = data.getU2At(OFFSET_OF_BOOTSTRAP_METHOD_ATTR_INDEX);
        this.nameAndTypeIndex = data.getU2At(OFFSET_OF_NAME_AND_TYPE_INDEX);
    }

    @Override
    public long getRawByteLength() {
        return 5;
    }

    @Override
    public void dump(Dumper d) {
        d.print(this.toString());
    }

    public int getBootstrapMethodAttrIndex() {
        return bootstrapMethodAttrIndex;
    }

    public ConstantPoolEntryNameAndType getNameAndTypeEntry() {
        return getCp().getNameAndTypeEntry(nameAndTypeIndex);
    }

    @Override
    public String toString() {
        return "InvokeDynamic value=" + bootstrapMethodAttrIndex + "," + nameAndTypeIndex;
    }
}
