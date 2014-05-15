package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntry;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryClass;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class AttributeExceptions extends Attribute {
    public final static String ATTRIBUTE_NAME = "Exceptions";

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_NUMBER_OF_EXCEPTIONS = 6;
    private static final long OFFSET_OF_EXCEPTION_TABLE = 8;
    private static final long OFFSET_OF_REMAINDER = 6;
    private final List<ConstantPoolEntryClass> exceptionClassList = ListFactory.newList();

    private final int length;

    public AttributeExceptions(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        short numExceptions = raw.getS2At(OFFSET_OF_NUMBER_OF_EXCEPTIONS);
        long offset = OFFSET_OF_EXCEPTION_TABLE;
        for (int x = 0; x < numExceptions; ++x, offset += 2) {
            exceptionClassList.add(cp.getClassEntry(raw.getS2At(offset)));
        }
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        for (ConstantPoolEntryClass exceptionClass : exceptionClassList) {
            collector.collect(exceptionClass.getTypeInstance());
        }
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d;
    }

    public List<ConstantPoolEntryClass> getExceptionClassList() {
        return exceptionClassList;
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }
}
