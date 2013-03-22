package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.util.bytestream.ByteData;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/03/2013
 * Time: 18:03
 */
public class AttributeRuntimeInvisibleParameterAnnotations extends AttributeParameterAnnotations {
    public static final String ATTRIBUTE_NAME = "RuntimeInvisibleParameterAnnotations";

    public AttributeRuntimeInvisibleParameterAnnotations(ByteData raw, ConstantPool cp) {
        super(raw, cp);
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }
}
