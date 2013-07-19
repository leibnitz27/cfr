package org.benf.cfr.reader.entityfactories;

import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntryUTF8;
import org.benf.cfr.reader.entities.attributes.*;
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

    public static Attribute build(ByteData raw, ConstantPool cp) {
        final short nameIndex = raw.getS2At(OFFSET_OF_ATTRIBUTE_NAME_INDEX);
        ConstantPoolEntryUTF8 name = (ConstantPoolEntryUTF8) cp.getEntry(nameIndex);
        String attributeName = name.getValue();

        if (AttributeCode.ATTRIBUTE_NAME.equals(attributeName)) {
            // Code attribute needs the signature of the method, so that we have type information for the
            // local variables.
            return new AttributeCode(raw, cp);
        } else if (AttributeLocalVariableTable.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeLocalVariableTable(raw, cp);
        } else if (AttributeSignature.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeSignature(raw, cp);
        } else if (AttributeConstantValue.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeConstantValue(raw, cp);
        } else if (AttributeLineNumberTable.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeLineNumberTable(raw, cp);
        } else if (AttributeExceptions.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeExceptions(raw, cp);
        } else if (AttributeDeprecated.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeDeprecated(raw, cp);
        } else if (AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeRuntimeVisibleAnnotations(raw, cp);
        } else if (AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeRuntimeInvisibleAnnotations(raw, cp);
        } else if (AttributeRuntimeVisibleParameterAnnotations.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeRuntimeVisibleParameterAnnotations(raw, cp);
        } else if (AttributeRuntimeInvisibleParameterAnnotations.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeRuntimeInvisibleParameterAnnotations(raw, cp);
        } else if (AttributeSourceFile.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeSourceFile(raw, cp);
        } else if (AttributeInnerClasses.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeInnerClasses(raw, cp);
        } else if (AttributeBootstrapMethods.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeBootstrapMethods(raw, cp);
        } else if (AttributeAnnotationDefault.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeAnnotationDefault(raw, cp);
        } else if (AttributeLocalVariableTypeTable.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeLocalVariableTypeTable(raw, cp);
        } else if (AttributeStackMapTable.ATTRIBUTE_NAME.equals(attributeName)) {
            return new AttributeStackMapTable(raw, cp);
        } else {
            return new AttributeUnknown(raw, attributeName);
            //throw new IllegalStateException(attributeName);
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
