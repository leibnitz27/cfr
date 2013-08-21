package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.bytestream.ByteData;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/03/2013
 * Time: 18:03
 */
public class AttributeRuntimeVisibleParameterAnnotations extends AttributeParameterAnnotations {
    public static final String ATTRIBUTE_NAME = "RuntimeVisibleParameterAnnotations";

    public AttributeRuntimeVisibleParameterAnnotations(ByteData raw, ConstantPool cp) {
        super(raw, cp);
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }
}
