package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 19:01
 * To change this template use File | Settings | File Templates.
 */
public class AttributeLocalVariableTable extends Attribute {
    public final static String ATTRIBUTE_NAME = "LocalVariableTable";

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_ENTRY_COUNT = 6;
    private static final long OFFSET_OF_ENTRIES = 8;
    private static final long OFFSET_OF_REMAINDER = 6;
    private final List<LocalVariableEntry> localVariableEntryList = ListFactory.newList();

    private final int length;

    public AttributeLocalVariableTable(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        short numLocalVariables = raw.getS2At(OFFSET_OF_ENTRY_COUNT);
        long offset = OFFSET_OF_ENTRIES;
        for (int x = 0; x < numLocalVariables; ++x) {
            short startPc = raw.getS2At(offset + 0);
            short length = raw.getS2At(offset + 2);
            short nameIndex = raw.getS2At(offset + 4);
            short descriptorIndex = raw.getS2At(offset + 6);
            short index = raw.getS2At(offset + 8);
            localVariableEntryList.add(new LocalVariableEntry(startPc, length, nameIndex, descriptorIndex, index));
            offset += 10;
        }
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp) {
    }

    public List<LocalVariableEntry> getLocalVariableEntryList() {
        return localVariableEntryList;
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }
}
