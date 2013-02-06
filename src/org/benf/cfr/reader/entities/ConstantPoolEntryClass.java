package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 20:38
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolEntryClass implements ConstantPoolEntry, ConstantPoolEntryLiteral {
    private final long OFFSET_OF_NAME_INDEX = 1;

    final short nameIndex;
    transient JavaTypeInstance javaTypeInstance = null;

    public ConstantPoolEntryClass(ByteData data) {
        this.nameIndex = data.getS2At(OFFSET_OF_NAME_INDEX);
    }

    @Override
    public long getRawByteLength() {
        return 3;
    }

    @Override
    public String toString() {
        return "CONSTANT_Class " + nameIndex;
    }

    public String getTextName(ConstantPool cp) {
        return ClassNameUtils.convert(cp.getUTF8Entry(nameIndex).getValue()) + ".class";
    }

    @Override
    public void dump(Dumper d, ConstantPool cp) {
        d.print("Class " + cp.getUTF8Entry(nameIndex).getValue());
    }

    public JavaTypeInstance getTypeInstance(ConstantPool cp) {
        if (javaTypeInstance == null) {
            String rawType = cp.getUTF8Entry(nameIndex).getValue();
            if (rawType.startsWith("[")) {
                javaTypeInstance = ConstantPoolUtils.decodeTypeTok(rawType, cp);
            } else {
                javaTypeInstance = new JavaRefTypeInstance(ClassNameUtils.convert(rawType), cp);
            }
        }
        return javaTypeInstance;
    }

    public short getNameIndex() {
        return nameIndex;
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }
}
