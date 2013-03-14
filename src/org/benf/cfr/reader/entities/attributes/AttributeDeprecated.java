package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 19:01
 * To change this template use File | Settings | File Templates.
 */
public class AttributeDeprecated extends Attribute {
    public static final String ATTRIBUTE_NAME = "Deprecated";

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_REMAINDER = 6;

    private final int length;
    private final ConstantPool cp;

    public AttributeDeprecated(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        this.cp = cp;
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp) {
        d.print("Deprecated");
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }

    @Override
    public String toString() {
        return "Deprecated";
    }
}
