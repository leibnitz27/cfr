package org.benf.cfr.reader.entities.constantpool;

import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.entities.AbstractConstantPoolEntry;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

public class ConstantPoolEntryNameAndType extends AbstractConstantPoolEntry {
    private static final long OFFSET_OF_NAME_INDEX = 1;
    private static final long OFFSET_OF_DESCRIPTOR_INDEX = 3;

    private final short nameIndex;
    private final short descriptorIndex;
    private StackDelta[] stackDelta = new StackDelta[2];

    public ConstantPoolEntryNameAndType(ConstantPool cp, ByteData data) {
        super(cp);
        this.nameIndex = data.getS2At(OFFSET_OF_NAME_INDEX);
        this.descriptorIndex = data.getS2At(OFFSET_OF_DESCRIPTOR_INDEX);
    }

    @Override
    public long getRawByteLength() {
        return 5;
    }

    @Override
    public void dump(Dumper d) {
        d.print("CONSTANT_NameAndType nameIndex=" + nameIndex + ", descriptorIndex=" + descriptorIndex);
    }

    @Override
    public String toString() {
        return "CONSTANT_NameAndType nameIndex=" + nameIndex + ", descriptorIndex=" + descriptorIndex;
    }

    public ConstantPoolEntryUTF8 getName() {
        return getCp().getUTF8Entry(nameIndex);
    }

    public ConstantPoolEntryUTF8 getDescriptor() {
        return getCp().getUTF8Entry(descriptorIndex);
    }

    public StackDelta getStackDelta(boolean member) {
        int idx = member ? 1 : 0;
        ConstantPool cp = getCp();
        if (stackDelta[idx] == null)
            stackDelta[idx] = ConstantPoolUtils.parseMethodPrototype(member, cp.getUTF8Entry(descriptorIndex), cp);
        return stackDelta[idx];
    }
}
