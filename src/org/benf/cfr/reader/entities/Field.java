package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.attributes.Attribute;
import org.benf.cfr.reader.entities.attributes.AttributeConstantValue;
import org.benf.cfr.reader.entities.attributes.AttributeSignature;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.util.CollectionUtils;
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
 * Date: 15/04/2011
 * Time: 21:50
 * To change this template use File | Settings | File Templates.
 */

/*
 * Too much in common with method - refactor.
 */

public class Field implements KnowsRawSize {
    private static final long OFFSET_OF_ACCESS_FLAGS = 0;
    private static final long OFFSET_OF_NAME_INDEX = 2;
    private static final long OFFSET_OF_DESCRIPTOR_INDEX = 4;
    private static final long OFFSET_OF_ATTRIBUTES_COUNT = 6;
    private static final long OFFSET_OF_ATTRIBUTES = 8;

    private final long length;
    private final short nameIndex;
    private final short descriptorIndex;
    private final Set<AccessFlag> accessFlags;
    private final Map<String, Attribute> attributes;
    private final TypedLiteral constantValue;
    private transient JavaTypeInstance cachedDecodedType;


    public Field(ByteData raw, final ConstantPool cp) {
        this.accessFlags = AccessFlag.build(raw.getS2At(OFFSET_OF_ACCESS_FLAGS));
        short attributes_count = raw.getS2At(OFFSET_OF_ATTRIBUTES_COUNT);
        ArrayList<Attribute> tmpAttributes = new ArrayList<Attribute>();
        tmpAttributes.ensureCapacity(attributes_count);
        long attributesLength = ContiguousEntityFactory.build(raw.getOffsetData(OFFSET_OF_ATTRIBUTES), attributes_count, tmpAttributes,
                new UnaryFunction<ByteData, Attribute>() {
                    @Override
                    public Attribute invoke(ByteData arg) {
                        return AttributeFactory.build(arg, cp);
                    }
                });
        this.attributes = ContiguousEntityFactory.addToMap(new HashMap<String, Attribute>(), tmpAttributes);
        this.descriptorIndex = raw.getS2At(OFFSET_OF_DESCRIPTOR_INDEX);
        this.nameIndex = raw.getS2At(OFFSET_OF_NAME_INDEX);
        this.length = OFFSET_OF_ATTRIBUTES + attributesLength;
        Attribute cvAttribute = attributes.get(AttributeConstantValue.ATTRIBUTE_NAME);
        this.constantValue = cvAttribute == null ? null : TypedLiteral.getConstantPoolEntry(cp, ((AttributeConstantValue) cvAttribute).getValue());
    }

    @Override
    public long getRawByteLength() {
        return length;
    }

    private AttributeSignature getSignatureAttribute() {
        Attribute attribute = attributes.get(AttributeSignature.ATTRIBUTE_NAME);
        if (attribute == null) return null;
        return (AttributeSignature) attribute;
    }

    public JavaTypeInstance getJavaTypeInstance(ConstantPool cp) {
        if (cachedDecodedType == null) {
            AttributeSignature sig = getSignatureAttribute();
            ConstantPoolEntryUTF8 signature = sig == null ? null : sig.getSignature();
            ConstantPoolEntryUTF8 descriptor = cp.getUTF8Entry(descriptorIndex);
            ConstantPoolEntryUTF8 prototype = null;
            if (signature == null) {
                prototype = descriptor;
            } else {
                prototype = signature;
            }
            /*
             * If we've got a signature, use that, otherwise use the descriptor.
             */
            cachedDecodedType = ConstantPoolUtils.decodeTypeTok(prototype.getValue(), cp);
        }
        return cachedDecodedType;
    }

    public String getFieldName(ConstantPool cp) {
        return cp.getUTF8Entry(nameIndex).getValue();
    }

    public boolean testAccessFlag(AccessFlag accessFlag) {
        return accessFlags.contains(accessFlag);
    }

    public void dump(Dumper d, ConstantPool cp) {
        StringBuilder sb = new StringBuilder();
        String prefix = CollectionUtils.join(accessFlags, " ");
        if (!prefix.isEmpty()) sb.append(prefix);
        JavaTypeInstance type = getJavaTypeInstance(cp);
        sb.append(' ').append(type.toString()).append(' ').append(getFieldName(cp));
        if (constantValue != null) {
            sb.append(" = ").append(constantValue);
        }
        sb.append(";\n");
        d.print(sb.toString());
    }
}
