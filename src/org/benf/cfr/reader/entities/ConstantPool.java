package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.bytestream.OffsettingByteData;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 19:20
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPool {
    private final long length;
    private final List<ConstantPoolEntry> entries;

    public ConstantPool(ByteData raw, short count) {
        ArrayList<ConstantPoolEntry> res = new ArrayList<ConstantPoolEntry>();
        count--;
        res.ensureCapacity(count);

        length = processRaw(raw, count, res);
        entries = res;
    }

    private long processRaw(ByteData raw, short count, List<ConstantPoolEntry> tgt) {
        OffsettingByteData data = raw.getOffsettingOffsetData(0);
        System.out.println("Processing " + count + " constpool entries.");
        for (short x = 0; x < count; ++x) {
            ConstantPoolEntry.Type type = ConstantPoolEntry.Type.get(data.getS1At(0));
            ConstantPoolEntry cpe;
            switch (type) {
                case CPT_NameAndType:
                    cpe = new ConstantPoolEntryNameAndType(data);
                    break;
                case CPT_String:
                    cpe = new ConstantPoolEntryString(data);
                    break;
                case CPT_FieldRef:
                    cpe = new ConstantPoolEntryFieldRef(data);
                    break;
                case CPT_MethodRef:
                    cpe = new ConstantPoolEntryMethodRef(data, false);
                    break;
                case CPT_InterfaceMethodRef:
                    cpe = new ConstantPoolEntryMethodRef(data, true);
                    break;
                case CPT_Class:
                    cpe = new ConstantPoolEntryClass(data);
                    break;
                case CPT_Double:
                    cpe = new ConstantPoolEntryDouble(data);
                    break;
                case CPT_Float:
                    cpe = new ConstantPoolEntryFloat(data);
                    break;
                case CPT_Long:
                    cpe = new ConstantPoolEntryLong(data);
                    break;
                case CPT_Integer:
                    cpe = new ConstantPoolEntryInteger(data);
                    break;
                case CPT_UTF8:
                    cpe = new ConstantPoolEntryUTF8(data);
                    break;
                default:
                    throw new ConfusedCFRException("Invalid constant pool entry : ");
            }
            System.out.println("" + (x + 1) + " : " + cpe);
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

    public ConstantPoolEntryUTF8 getUTF8Entry(int index) {
        return (ConstantPoolEntryUTF8) getEntry(index);
    }

    public ConstantPoolEntryNameAndType getNameAndTypeEntry(int index) {
        return (ConstantPoolEntryNameAndType) getEntry(index);
    }

    public ConstantPoolEntryClass getClassEntry(int index) {
        return (ConstantPoolEntryClass) getEntry(index);
    }
}
