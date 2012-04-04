package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.attributes.Attribute;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 19:01
 * To change this template use File | Settings | File Templates.
 */
public class AttributeUnknown extends Attribute {
    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_REMAINDER = 6;

    private final int length;
    private final String name;

    public AttributeUnknown(ByteData raw, String name)
    {
        this.length = raw.getU4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        this.name = name;
    }

    @Override
    public String getRawName()
    {
        return name;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp)
    {
        d.print("Unknown Attribute : " + name);
    }

    @Override
    public long getRawByteLength()
    {
        return OFFSET_OF_REMAINDER + length;
    }
}
