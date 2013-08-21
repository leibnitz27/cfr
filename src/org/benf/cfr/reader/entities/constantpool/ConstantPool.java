package org.benf.cfr.reader.entities.constantpool;

import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.entities.ClassCache;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.bytestream.OffsettingByteData;
import org.benf.cfr.reader.util.getopt.CFRState;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 19:20
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPool {
    private static final Logger logger = LoggerFactory.create(ConstantPool.class);

    private final long length;
    private final List<ConstantPoolEntry> entries;
    private final CFRState cfrState;
    private final ClassCache classCache;
    private final ClassFile classFile;
    private String comparisonKey;
    private boolean isLoaded = false;

    public ConstantPool(ClassFile classFile, CFRState cfrState, ByteData raw, short count) {
        this.classFile = classFile;
        this.cfrState = cfrState;
        ArrayList<ConstantPoolEntry> res = new ArrayList<ConstantPoolEntry>();
        count--;
        res.ensureCapacity(count);

        length = processRaw(raw, count, res);
        entries = res;
        this.classCache = cfrState.getClassCache();
        this.isLoaded = true;
    }

    public CFRState getCFRState() {
        return cfrState;
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    private long processRaw(ByteData raw, short count, List<ConstantPoolEntry> tgt) {
        OffsettingByteData data = raw.getOffsettingOffsetData(0);
        logger.info("Processing " + count + " constpool entries.");
        for (short x = 0; x < count; ++x) {
            ConstantPoolEntry.Type type = ConstantPoolEntry.Type.get(data.getS1At(0));
            ConstantPoolEntry cpe;
            switch (type) {
                case CPT_NameAndType:
                    cpe = new ConstantPoolEntryNameAndType(this, data);
                    break;
                case CPT_String:
                    cpe = new ConstantPoolEntryString(this, data);
                    break;
                case CPT_FieldRef:
                    cpe = new ConstantPoolEntryFieldRef(this, data);
                    break;
                case CPT_MethodRef:
                    cpe = new ConstantPoolEntryMethodRef(this, data, false);
                    break;
                case CPT_InterfaceMethodRef:
                    cpe = new ConstantPoolEntryMethodRef(this, data, true);
                    break;
                case CPT_Class:
                    cpe = new ConstantPoolEntryClass(this, data);
                    break;
                case CPT_Double:
                    cpe = new ConstantPoolEntryDouble(this, data);
                    break;
                case CPT_Float:
                    cpe = new ConstantPoolEntryFloat(this, data);
                    break;
                case CPT_Long:
                    cpe = new ConstantPoolEntryLong(this, data);
                    break;
                case CPT_Integer:
                    cpe = new ConstantPoolEntryInteger(this, data);
                    break;
                case CPT_UTF8:
                    cpe = new ConstantPoolEntryUTF8(this, data);
                    break;
                case CPT_MethodHandle:
                    cpe = new ConstantPoolEntryMethodHandle(this, data);
                    break;
                case CPT_MethodType:
                    cpe = new ConstantPoolEntryMethodType(this, data);
                    break;
                case CPT_InvokeDynamic:
                    cpe = new ConstantPoolEntryInvokeDynamic(this, data);
                    break;
                default:
                    throw new ConfusedCFRException("Invalid constant pool entry : " + type);
            }
            logger.info("" + (x + 1) + " : " + cpe);
            tgt.add(cpe);
            switch (type) {
                case CPT_Double:
                case CPT_Long:
                    tgt.add(null);
                    x++;
                    break;
            }

            long size = cpe.getRawByteLength();
            data.advance(size);
        }
        return data.getOffset();
    }

    public long getRawByteLength() {
        return length;
    }

    public ConstantPoolEntry getEntry(int index) {
        if (index == 0) throw new ConfusedCFRException("Attempt to fetch element 0 from constant pool");
        // NB: Constant pool entries are 1 based.
        return entries.get(index - 1);
    }
//
//    private void setEntry(int index, ConstantPoolEntry constantPoolEntry) {
//        if (index == 0) throw new ConfusedCFRException("Attempt to set element 0 in constant pool");
//        // NB: Constant pool entries are 1 based.
//        entries.set(index - 1, constantPoolEntry);
//    }

    public ConstantPoolEntryUTF8 getUTF8Entry(int index) {
        return (ConstantPoolEntryUTF8) getEntry(index);
    }

    public ConstantPoolEntryNameAndType getNameAndTypeEntry(int index) {
        return (ConstantPoolEntryNameAndType) getEntry(index);
    }

    public ConstantPoolEntryMethodHandle getMethodHandleEntry(int index) {
        return (ConstantPoolEntryMethodHandle) getEntry(index);
    }

    public ConstantPoolEntryMethodRef getMethodRefEntry(int index) {
        return (ConstantPoolEntryMethodRef) getEntry(index);
    }

    public ConstantPoolEntryClass getClassEntry(int index) {
        return (ConstantPoolEntryClass) getEntry(index);
    }

    public ClassCache getClassCache() {
        return classCache;
    }

    @Override
    public boolean equals(Object o) {
        getComparisonKey();
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConstantPool that = (ConstantPool) o;

        if (!comparisonKey.equals(that.comparisonKey)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        getComparisonKey();
        return comparisonKey.hashCode();
    }

    private void getComparisonKey() {
        if (comparisonKey == null) comparisonKey = classFile.getFilePath();
    }
}
