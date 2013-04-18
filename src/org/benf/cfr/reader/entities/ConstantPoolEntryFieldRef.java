package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
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
public class ConstantPoolEntryFieldRef extends AbstractConstantPoolEntry {
    private final long OFFSET_OF_CLASS_INDEX = 1;
    private final long OFFSET_OF_NAME_AND_TYPE_INDEX = 3;

    final short classIndex;
    final short nameAndTypeIndex;
    JavaTypeInstance cachedDecodedType;

    public ConstantPoolEntryFieldRef(ConstantPool cp, ByteData data) {
        super(cp);
        this.classIndex = data.getS2At(OFFSET_OF_CLASS_INDEX);
        this.nameAndTypeIndex = data.getS2At(OFFSET_OF_NAME_AND_TYPE_INDEX);
    }

    @Override
    public long getRawByteLength() {
        return 5;
    }

    @Override
    public void dump(Dumper d) {
        ConstantPool cp = getCp();
        d.print("Field " +
                cp.getNameAndTypeEntry(nameAndTypeIndex).getName().getValue() + ":" +
                getJavaTypeInstance());
    }

    public short getClassIndex() {
        return classIndex;
    }

    public ConstantPoolEntryClass getClassEntry() {
        return getCp().getClassEntry(classIndex);
    }

    public ConstantPoolEntryNameAndType getNameAndTypeEntry() {
        return getCp().getNameAndTypeEntry(nameAndTypeIndex);
    }

    public String getLocalName() {
        return getCp().getNameAndTypeEntry(nameAndTypeIndex).getName().getValue();
    }

    public JavaTypeInstance getJavaTypeInstance() {
        if (cachedDecodedType == null) {
            ConstantPool cp = getCp();
            cachedDecodedType = ConstantPoolUtils.decodeTypeTok(cp.getNameAndTypeEntry(nameAndTypeIndex).getDescriptor().getValue(), cp);
        }
        return cachedDecodedType;
    }

    public StackType getStackType() {
        return getJavaTypeInstance().getStackType();
    }


    @Override
    public String toString() {
        return "ConstantPool_FieldRef [classIndex:" + classIndex + ", nameAndTypeIndex:" + nameAndTypeIndex + "]";
    }
}
