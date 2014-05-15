package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.bytestream.ByteData;

public class AttributeRuntimeInvisibleAnnotations extends AttributeAnnotations {
    public static final String ATTRIBUTE_NAME = "RuntimeInvisibleAnnotations";

    public AttributeRuntimeInvisibleAnnotations(ByteData raw, ConstantPool cp) {
        super(raw, cp);
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }
}
