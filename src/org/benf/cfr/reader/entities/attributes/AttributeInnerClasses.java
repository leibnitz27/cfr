package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryClass;
import org.benf.cfr.reader.entities.innerclass.InnerClassAttributeInfo;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class AttributeInnerClasses extends Attribute {
    public static final String ATTRIBUTE_NAME = "InnerClasses";

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_REMAINDER = 6;
    private static final long OFFSET_OF_NUMBER_OF_CLASSES = 6;
    private static final long OFFSET_OF_CLASS_ARRAY = 8;


    private final int length;
    private final List<InnerClassAttributeInfo> innerClassAttributeInfoList = ListFactory.newList();

    private static JavaTypeInstance getOptClass(int idx, ConstantPool cp) {
        if (idx == 0) {
            return null;
        }
        return cp.getClassEntry(idx).getTypeInstance();
    }

    private static String getOptName(int idx, ConstantPool cp) {
        if (idx == 0) {
            return null;
        }
        return cp.getUTF8Entry(idx).getValue();
    }

    private static Pair<JavaTypeInstance, JavaTypeInstance> getInnerOuter(int idxinner, int idxouter, ConstantPool cp) {
        if (idxinner == 0 || idxouter == 0) {
            return Pair.make(getOptClass(idxinner, cp), getOptClass(idxouter, cp));
        }
        ConstantPoolEntryClass cpecInner = cp.getClassEntry(idxinner);
        ConstantPoolEntryClass cpecOuter = cp.getClassEntry(idxouter);
        JavaTypeInstance innerType = cpecInner.getTypeInstanceKnownOuter(cpecOuter);
        JavaTypeInstance outerType = cpecOuter.getTypeInstanceKnownInner(cpecInner);
        return Pair.make(innerType, outerType);
    }

    public AttributeInnerClasses(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        int numberInnerClasses = raw.getU2At(OFFSET_OF_NUMBER_OF_CLASSES);
        long offset = OFFSET_OF_CLASS_ARRAY;
        for (int x = 0; x < numberInnerClasses; ++x) {
            int innerClassInfoIdx = raw.getU2At(offset);
            offset += 2;
            int outerClassInfoIdx = raw.getU2At(offset);
            offset += 2;
            int innerNameIdx = raw.getU2At(offset);
            offset += 2;
            int innerAccessFlags = raw.getU2At(offset);
            offset += 2;
            Pair<JavaTypeInstance, JavaTypeInstance> innerOuter = getInnerOuter(innerClassInfoIdx, outerClassInfoIdx, cp);
            JavaTypeInstance innerClassType = innerOuter.getFirst();
            JavaTypeInstance outerClassType = innerOuter.getSecond();
            // MarkAnonymous feels like a bit of a hack, but otherwise we need to propagate this information
            // the whole way down the type creation path, and this is the only place we care about it.
            // May add that in later.
            // if (outerClassType == null) {
            if (outerClassType == null) {
                boolean methodScoped = innerNameIdx == 0;
                innerClassType.getInnerClassHereInfo().markMethodScoped(methodScoped);
            }
            innerClassAttributeInfoList.add(new InnerClassAttributeInfo(
                    innerClassType,
                    outerClassType,
                    getOptName(innerNameIdx, cp),
                    AccessFlag.build(innerAccessFlags)
            ));
        }
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print(ATTRIBUTE_NAME);
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }

    public List<InnerClassAttributeInfo> getInnerClassAttributeInfoList() {
        return innerClassAttributeInfoList;
    }

    @Override
    public String toString() {
        return ATTRIBUTE_NAME;
    }
}
