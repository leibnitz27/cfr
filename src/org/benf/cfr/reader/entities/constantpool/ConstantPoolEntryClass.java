package org.benf.cfr.reader.entities.constantpool;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.entities.AbstractConstantPoolEntry;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

public class ConstantPoolEntryClass extends AbstractConstantPoolEntry implements ConstantPoolEntryLiteral {
    private static final long OFFSET_OF_NAME_INDEX = 1;

    final short nameIndex;
    transient JavaTypeInstance javaTypeInstance = null;

    public ConstantPoolEntryClass(ConstantPool cp, ByteData data) {
        super(cp);
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

    public String getTextPath() {
        return ClassNameUtils.convertFromPath(getCp().getUTF8Entry(nameIndex).getValue()) + ".class";
    }

    public String getFilePath() {
        return getCp().getUTF8Entry(nameIndex).getValue() + ".class";
    }

    @Override
    public void dump(Dumper d) {
        d.print("Class " + getCp().getUTF8Entry(nameIndex).getValue());
    }

    public String getPackageName() {
        String full = ClassNameUtils.convertFromPath(getCp().getUTF8Entry(nameIndex).getValue());
        int idx = full.lastIndexOf('.');
        if (idx == -1) return "";
        return full.substring(0, idx);
    }

    public JavaTypeInstance convertFromString(String rawType) {
        if (rawType.startsWith("[")) {
            return ConstantPoolUtils.decodeTypeTok(rawType, getCp());
        } else {
            return getCp().getClassCache().getRefClassFor(ClassNameUtils.convertFromPath(rawType));
        }
    }

    public JavaTypeInstance getTypeInstance() {
        if (javaTypeInstance == null) {
            String rawType = getCp().getUTF8Entry(nameIndex).getValue();
            javaTypeInstance = convertFromString(rawType);
        }
        return javaTypeInstance;
    }

    public JavaTypeInstance getTypeInstanceKnownOuter(ConstantPoolEntryClass outer) {
        if (javaTypeInstance != null) {
            return javaTypeInstance;
        }
        String thisInnerType = getCp().getUTF8Entry(nameIndex).getValue();
        String thisOuterType = getCp().getUTF8Entry(outer.nameIndex).getValue();
        Pair<JavaRefTypeInstance, JavaRefTypeInstance> pair = getCp().getClassCache().getRefClassForInnerOuterPair(thisInnerType, thisOuterType);
        javaTypeInstance = pair.getFirst();
        return javaTypeInstance;
    }

    public JavaTypeInstance getTypeInstanceKnownInner(ConstantPoolEntryClass inner) {
        if (javaTypeInstance != null) {
            return javaTypeInstance;
        }
        String thisInnerType = getCp().getUTF8Entry(inner.nameIndex).getValue();
        String thisOuterType = getCp().getUTF8Entry(nameIndex).getValue();
        Pair<JavaRefTypeInstance, JavaRefTypeInstance> pair = getCp().getClassCache().getRefClassForInnerOuterPair(thisInnerType, thisOuterType);
        javaTypeInstance = pair.getSecond();
        return javaTypeInstance;
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }
}
