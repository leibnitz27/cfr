package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.attributes.*;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryUTF8;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolUtils;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.CollectionUtils;
import org.benf.cfr.reader.util.KnowsRawSize;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/*
 * Too much in common with method - refactor.
 */

public class Field implements KnowsRawSize, TypeUsageCollectable {
    private static final long OFFSET_OF_ACCESS_FLAGS = 0;
    private static final long OFFSET_OF_NAME_INDEX = 2;
    private static final long OFFSET_OF_DESCRIPTOR_INDEX = 4;
    private static final long OFFSET_OF_ATTRIBUTES_COUNT = 6;
    private static final long OFFSET_OF_ATTRIBUTES = 8;

    private final ConstantPool cp;
    private final long length;
    private final short descriptorIndex;
    private final Set<AccessFlag> accessFlags;
    private final Map<String, Attribute> attributes;
    private final TypedLiteral constantValue;
    private final String rawFieldName;
    private final String fieldName;
    private boolean disambiguate;
    private transient JavaTypeInstance cachedDecodedType;

    public Field(ByteData raw, final ConstantPool cp) {
        this.cp = cp;
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
        AccessFlag.applyAttributes(attributes, accessFlags);
        this.descriptorIndex = raw.getS2At(OFFSET_OF_DESCRIPTOR_INDEX);
        short nameIndex = raw.getS2At(OFFSET_OF_NAME_INDEX);
        this.length = OFFSET_OF_ATTRIBUTES + attributesLength;
        Attribute cvAttribute = attributes.get(AttributeConstantValue.ATTRIBUTE_NAME);
        this.constantValue = cvAttribute == null ? null : TypedLiteral.getConstantPoolEntry(cp, ((AttributeConstantValue) cvAttribute).getValue());
        this.fieldName = cp.getUTF8Entry(nameIndex).getValue();
        String rawfieldName = cp.getUTF8Entry(nameIndex).getRawValue();
        if (this.fieldName.equals(rawfieldName)) rawfieldName = this.fieldName;
        this.rawFieldName = rawfieldName;
        this.disambiguate = false;
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

    public JavaTypeInstance getJavaTypeInstance() {
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

    public String getRawFieldName() {
        return rawFieldName;
    }

    public void setDisambiguate() {
        disambiguate = true;
    }

    public String getFieldName() {
        if (disambiguate) {
            String rawName = getJavaTypeInstance().getRawName();
            rawName = rawName.replace("[]", "_arr").replaceAll("[*?<>. ]","_");
            return fieldName + "_" + rawName;
        }
        return fieldName;
    }

    public boolean testAccessFlag(AccessFlag accessFlag) {
        return accessFlags.contains(accessFlag);
    }

    public TypedLiteral getConstantValue() {
        return constantValue;
    }

    private <T extends Attribute> T getAttributeByName(String name) {
        Attribute attribute = attributes.get(name);
        if (attribute == null) return null;
        @SuppressWarnings("unchecked")
        T tmp = (T) attribute;
        return tmp;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(getJavaTypeInstance());
        collector.collectFrom(getAttributeByName(AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(getAttributeByName(AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME));
    }

    public void dump(Dumper d, String name) {
        AttributeRuntimeVisibleAnnotations runtimeVisibleAnnotations = getAttributeByName(AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME);
        AttributeRuntimeInvisibleAnnotations runtimeInvisibleAnnotations = getAttributeByName(AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME);
        if (runtimeVisibleAnnotations != null) runtimeVisibleAnnotations.dump(d);
        if (runtimeInvisibleAnnotations != null) runtimeInvisibleAnnotations.dump(d);
        String prefix = CollectionUtils.join(accessFlags, " ");
        if (!prefix.isEmpty()) {
            d.print(prefix).print(' ');
        }
        JavaTypeInstance type = getJavaTypeInstance();
        d.dump(type).print(' ').identifier(name);
    }
}
