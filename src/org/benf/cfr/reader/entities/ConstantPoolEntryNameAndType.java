package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.stack.StackDelta;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 20:34
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolEntryNameAndType extends AbstractConstantPoolEntry {
    private final long OFFSET_OF_NAME_INDEX = 1;
    private final long OFFSET_OF_DESCRIPTOR_INDEX = 3;

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
    public void dump(Dumper d, ConstantPool cp) {
        d.print("CONSTANT_NameAndType nameIndex=" + nameIndex + ", descriptorIndex=" + descriptorIndex);
    }

    @Override
    public String toString() {
        return "CONSTANT_NameAndType nameIndex=" + nameIndex + ", descriptorIndex=" + descriptorIndex;
    }

    public ConstantPoolEntryUTF8 getName(ConstantPool cp) {
        return cp.getUTF8Entry(nameIndex);
    }

    public ConstantPoolEntryUTF8 getDescriptor(ConstantPool cp) {
        return cp.getUTF8Entry(descriptorIndex);
    }

    public short getDescriptorIndex() {
        return descriptorIndex;
    }

    public StackDelta getStackDelta(boolean member, ConstantPool cp) {
        int idx = member ? 1 : 0;
        if (stackDelta[idx] == null)
            stackDelta[idx] = ConstantPoolUtils.parseMethodPrototype(member, cp.getUTF8Entry(descriptorIndex), cp);
        return stackDelta[idx];
    }
}
