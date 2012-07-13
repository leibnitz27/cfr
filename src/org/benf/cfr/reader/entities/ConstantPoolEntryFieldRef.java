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
public class ConstantPoolEntryFieldRef implements ConstantPoolEntry {
    private final long OFFSET_OF_CLASS_INDEX = 1;
    private final long OFFSET_OF_NAME_AND_TYPE_INDEX = 3;

    final short classIndex;
    final short nameAndTypeIndex;
    StackType cachedDecodedType;

    public ConstantPoolEntryFieldRef(ByteData data) {
        this.classIndex = data.getS2At(OFFSET_OF_CLASS_INDEX);
        this.nameAndTypeIndex = data.getS2At(OFFSET_OF_NAME_AND_TYPE_INDEX);
    }

    @Override
    public long getRawByteLength() {
        return 5;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp) {
        d.print("Field " +
                cp.getNameAndTypeEntry(nameAndTypeIndex).getName(cp).getValue() + ":" +
                cp.getNameAndTypeEntry(nameAndTypeIndex).getDescriptor(cp).getValue());
    }

    public short getClassIndex() {
        return classIndex;
    }

    public String getLocalName(ConstantPool cp) {
        return cp.getNameAndTypeEntry(nameAndTypeIndex).getName(cp).getValue();
    }

    public StackType getStackType(ConstantPool cp) {
        if (cachedDecodedType == null) {
            cachedDecodedType = ConstantPoolUtils.decodeTypeTok(cp.getNameAndTypeEntry(nameAndTypeIndex).getDescriptor(cp).getValue()).getStackType();
        }
        return cachedDecodedType;
    }

}
