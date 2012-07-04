package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.entities.attributes.Attribute;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.util.KnowsRawSize;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 17/04/2011
 * Time: 21:32
 * To change this template use File | Settings | File Templates.
 */

/* Too much in common with field - refactor.
 *
 */

public class Method implements KnowsRawSize {
    private static final long OFFSET_OF_ACCESS_FLAGS = 0;
    private static final long OFFSET_OF_NAME_INDEX = 2;
    private static final long OFFSET_OF_DESCRIPTOR_INDEX = 4;
    private static final long OFFSET_OF_ATTRIBUTES_COUNT = 6;
    private static final long OFFSET_OF_ATTRIBUTES = 8;

    private final long length;
    private final Set<AccessFlagMethod> accessFlags;
    private final Map<String, Attribute> attributes;
    private final short nameIndex;
    private final short descriptorIndex;

    public Method(ByteData raw, final ConstantPool cp) {
        this.nameIndex = raw.getS2At(OFFSET_OF_NAME_INDEX);
        this.accessFlags = AccessFlagMethod.build(raw.getS2At(OFFSET_OF_ACCESS_FLAGS));
        short numAttributes = raw.getS2At(OFFSET_OF_ATTRIBUTES_COUNT);
        ArrayList<Attribute> tmpAttributes = new ArrayList<Attribute>();
        tmpAttributes.ensureCapacity(numAttributes);
        long attributesLength = ContiguousEntityFactory.build(raw.getOffsetData(OFFSET_OF_ATTRIBUTES), numAttributes, tmpAttributes,
                new UnaryFunction<ByteData, Attribute>() {
                    @Override
                    public Attribute invoke(ByteData arg) {
                        return AttributeFactory.build(arg, cp);
                    }
                });
        this.attributes = ContiguousEntityFactory.addToMap(new HashMap<String, Attribute>(), tmpAttributes);
        this.descriptorIndex = raw.getS2At(OFFSET_OF_DESCRIPTOR_INDEX);
        this.length = OFFSET_OF_ATTRIBUTES + attributesLength;
    }

    @Override
    public long getRawByteLength() {
        return length;
    }

    public String getName(ConstantPool cp) {
        return cp.getUTF8Entry(nameIndex).getValue();
    }

    public void dump(Dumper d, ConstantPool cp) {
        d.newln();
        cp.getEntry(nameIndex).dump(d, cp);
        d.newln();
        cp.getEntry(descriptorIndex).dump(d, cp);
        d.newln().print(accessFlags.toString());
        d.newln().print(attributes.size() + " attributes.");
        for (Attribute attr : attributes.values()) {
            d.newln();
            attr.dump(d, cp);
        }
        d.newln();
    }
}
