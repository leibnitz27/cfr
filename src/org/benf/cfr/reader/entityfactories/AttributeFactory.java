package org.benf.cfr.reader.entityfactories;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryUTF8;
import org.benf.cfr.reader.entities.attributes.*;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;

public class AttributeFactory {
    private static final long OFFSET_OF_ATTRIBUTE_NAME_INDEX = 0;

    public static Attribute build(ByteData raw, ConstantPool cp, ClassFileVersion classFileVersion) {
        final int nameIndex = raw.getU2At(OFFSET_OF_ATTRIBUTE_NAME_INDEX);
        ConstantPoolEntryUTF8 name = (ConstantPoolEntryUTF8) cp.getEntry(nameIndex);
        String attributeName = name.getValue();

        /*
         * This absolutely could be replaced with a string switch, but I'm sticking to j6,
         * as I want to mandate the minimum sane requirements.
         */
        if (AttributeCode.ATTRIBUTE_NAME.equals(attributeName)) {
            // Code attribute needs the signature of the method, so that we have type information for the
            // local variables.
            return new AttributeCode(raw, cp, classFileVersion);
        }

        try {
            if (AttributeLocalVariableTable.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeLocalVariableTable(raw);
            } else if (AttributeSignature.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeSignature(raw, cp);
            } else if (AttributeConstantValue.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeConstantValue(raw, cp);
            } else if (AttributeLineNumberTable.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeLineNumberTable(raw);
            } else if (AttributeExceptions.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeExceptions(raw, cp);
            } else if (AttributeEnclosingMethod.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeEnclosingMethod(raw);
            } else if (AttributeDeprecated.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeDeprecated(raw);
            } else if (AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeRuntimeVisibleAnnotations(raw, cp);
            } else if (AttributeRuntimeVisibleTypeAnnotations.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeRuntimeVisibleTypeAnnotations(raw, cp);
            } else if (AttributeRuntimeInvisibleTypeAnnotations.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeRuntimeInvisibleTypeAnnotations(raw, cp);
            } else if (AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeRuntimeInvisibleAnnotations(raw, cp);
            } else if (AttributeRuntimeVisibleParameterAnnotations.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeRuntimeVisibleParameterAnnotations(raw, cp);
            } else if (AttributeRuntimeInvisibleParameterAnnotations.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeRuntimeInvisibleParameterAnnotations(raw, cp);
            } else if (AttributeSourceFile.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeSourceFile(raw);
            } else if (AttributeInnerClasses.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeInnerClasses(raw, cp);
            } else if (AttributeBootstrapMethods.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeBootstrapMethods(raw, cp);
            } else if (AttributeAnnotationDefault.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeAnnotationDefault(raw, cp);
            } else if (AttributeLocalVariableTypeTable.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeLocalVariableTypeTable(raw);
            } else if (AttributeStackMapTable.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeStackMapTable(raw, cp);
            } else if (AttributeSynthetic.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeSynthetic(raw);
            } else if (AttributeScalaSig.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeScalaSig(raw);
            } else if (AttributeScala.ATTRIBUTE_NAME.equals(attributeName)) {
                return new AttributeScala(raw);
            }
        } catch (Exception e) {
            // Can't handle it? Continue and process as an unknown attribute.
        }
        return new AttributeUnknown(raw, attributeName);
    }

    public static UnaryFunction<ByteData, Attribute> getBuilder(ConstantPool cp, ClassFileVersion classFileVersion) {
        return new AttributeBuilder(cp, classFileVersion);
    }

    private static class AttributeBuilder implements UnaryFunction<ByteData, Attribute> {
        private final ConstantPool cp;
        private final ClassFileVersion classFileVersion;

        AttributeBuilder(ConstantPool cp, ClassFileVersion classFileVersion) {
            this.cp = cp;
            this.classFileVersion = classFileVersion;
        }

        @Override
        public Attribute invoke(ByteData arg) {
            return AttributeFactory.build(arg, cp, classFileVersion);
        }
    }
}
