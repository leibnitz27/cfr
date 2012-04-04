package org.benf.cfr.reader.entityfactories;

import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.attributes.Attribute;
import org.benf.cfr.reader.entities.attributes.AttributeCode;
import org.benf.cfr.reader.entities.attributes.AttributeLocalVariableTable;
import org.benf.cfr.reader.entities.attributes.AttributeUnknown;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class AttributeFactory {
    private static final long OFFSET_OF_ATTRIBUTE_NAME_INDEX = 0;

    public static Attribute build(ByteData raw, ConstantPool cp)
    {
        final short nameIndex = raw.getU2At(OFFSET_OF_ATTRIBUTE_NAME_INDEX);
        ConstantPoolEntryUTF8 name = (ConstantPoolEntryUTF8)cp.getEntry(nameIndex);
        String attributeName = name.getValue();

        if ("Code".equals(attributeName)) {
            return new AttributeCode(raw, cp);
        } else if ("LocalVariableTable".equals(attributeName)) {
            return new AttributeLocalVariableTable(raw, cp);
        } else {
            return new AttributeUnknown(raw, attributeName);
        }

    }

    public static UnaryFunction<ByteData, Attribute> getBuilder(ConstantPool cp) {
        return new AttributeBuilder(cp);
    }

    private static class AttributeBuilder implements UnaryFunction<ByteData, Attribute> {
        private final ConstantPool cp;

        public AttributeBuilder(ConstantPool cp) {
            this.cp = cp;
        }

        @Override
        public Attribute invoke(ByteData arg) {
            return AttributeFactory.build(arg, cp);
        }
    }
}
