package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.DeclarationAnnotationHelper;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MiscAnnotations;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeAnnotationHelper;
import org.benf.cfr.reader.bytecode.analysis.types.DeclarationAnnotationHelper.DeclarationAnnotationsInfo;
import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.annotations.AnnotationTableEntry;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.attributes.*;
import org.benf.cfr.reader.entities.classfilehelpers.VisibilityHelper;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryUTF8;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolUtils;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.CollectionUtils;
import org.benf.cfr.reader.util.KnowsRawSize;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.ArrayList;
import java.util.List;
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
    private final int descriptorIndex;
    private final Set<AccessFlag> accessFlags;
    private final AttributeMap attributes;
    private final TypedLiteral constantValue;
    private final String fieldName;
    private boolean disambiguate;
    private transient JavaTypeInstance cachedDecodedType;

    public Field(ByteData raw, final ConstantPool cp, final ClassFileVersion classFileVersion) {
        this.cp = cp;
        this.accessFlags = AccessFlag.build(raw.getU2At(OFFSET_OF_ACCESS_FLAGS));
        int attributes_count = raw.getU2At(OFFSET_OF_ATTRIBUTES_COUNT);
        ArrayList<Attribute> tmpAttributes = new ArrayList<Attribute>();
        tmpAttributes.ensureCapacity(attributes_count);
        long attributesLength = ContiguousEntityFactory.build(raw.getOffsetData(OFFSET_OF_ATTRIBUTES), attributes_count, tmpAttributes,
                AttributeFactory.getBuilder(cp, classFileVersion));

        this.attributes = new AttributeMap(tmpAttributes);
        AccessFlag.applyAttributes(attributes, accessFlags);
        this.descriptorIndex = raw.getU2At(OFFSET_OF_DESCRIPTOR_INDEX);
        int nameIndex = raw.getU2At(OFFSET_OF_NAME_INDEX);
        this.length = OFFSET_OF_ATTRIBUTES + attributesLength;
        AttributeConstantValue cvAttribute = attributes.getByName(AttributeConstantValue.ATTRIBUTE_NAME);
        this.fieldName = cp.getUTF8Entry(nameIndex).getValue();
        this.disambiguate = false;
        TypedLiteral constValue = null;
        if (cvAttribute != null) {
            constValue = TypedLiteral.getConstantPoolEntry(cp, ((AttributeConstantValue) cvAttribute).getValue());
            if (constValue.getType() == TypedLiteral.LiteralType.Integer) {
                // Need to check if the field is actually something smaller than an integer, and downcast the
                // literal - sufficiently constructed to do this. (although naughty).
                JavaTypeInstance thisType = getJavaTypeInstance();
                if (thisType instanceof RawJavaType) {
                    constValue = TypedLiteral.shrinkTo(constValue, (RawJavaType)thisType);
                }
            }
        }
        this.constantValue = constValue;
    }

    @Override
    public long getRawByteLength() {
        return length;
    }

    private AttributeSignature getSignatureAttribute() {
        return attributes.getByName(AttributeSignature.ATTRIBUTE_NAME);
    }

    public JavaTypeInstance getJavaTypeInstance() {
        if (cachedDecodedType == null) {
            AttributeSignature sig = getSignatureAttribute();
            ConstantPoolEntryUTF8 signature = sig == null ? null : sig.getSignature();
            ConstantPoolEntryUTF8 descriptor = cp.getUTF8Entry(descriptorIndex);
            ConstantPoolEntryUTF8 prototype;
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

    void setDisambiguate() {
        disambiguate = true;
    }

    public String getFieldName() {
        if (disambiguate) {
            return "var_" + ClassNameUtils.getTypeFixPrefix(getJavaTypeInstance()) + fieldName;
        }
        return fieldName;
    }

    public boolean testAccessFlag(AccessFlag accessFlag) {
        return accessFlags.contains(accessFlag);
    }

    public Set<AccessFlag> getAccessFlags() {
        return accessFlags;
    }

    public TypedLiteral getConstantValue() {
        return constantValue;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(getJavaTypeInstance());
        collector.collectFrom(attributes.getByName(AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(attributes.getByName(AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(attributes.getByName(AttributeRuntimeVisibleTypeAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(attributes.getByName(AttributeRuntimeInvisibleTypeAnnotations.ATTRIBUTE_NAME));
    }

    public void dump(Dumper d, String name, ClassFile owner, boolean asRecordField) {
        JavaTypeInstance type = getJavaTypeInstance();

        List<AnnotationTableEntry> declarationAnnotations = MiscAnnotations.BasicAnnotations(attributes);
        TypeAnnotationHelper tah = TypeAnnotationHelper.create(attributes, TypeAnnotationEntryValue.type_field);
        List<AnnotationTableTypeEntry> fieldTypeAnnotations = tah == null ? null : tah.getEntries();

        DeclarationAnnotationsInfo annotationsInfo = DeclarationAnnotationHelper.getDeclarationInfo(type, declarationAnnotations, fieldTypeAnnotations);
        /*
         * TODO: This is incorrect, but currently cannot easily influence whether the dumped type is admissible
         * Therefore assume it is always admissible unless required not to
         * (even though then the dumped type might still be admissible)
         */
        boolean usesAdmissibleType = !annotationsInfo.requiresNonAdmissibleType();
        List<AnnotationTableEntry> declAnnotationsToDump = annotationsInfo.getDeclarationAnnotations(usesAdmissibleType);
        List<AnnotationTableTypeEntry> typeAnnotationsToDump = annotationsInfo.getTypeAnnotations(usesAdmissibleType);

        for (AnnotationTableEntry annotation : declAnnotationsToDump) {
            annotation.dump(d);
            if (asRecordField) {
                d.print(" ");
            } else {
                d.newln();
            }
        }

        if (!asRecordField) {
            Set<AccessFlag> accessFlagsLocal = accessFlags;
            if (cp.getDCCommonState().getOptions().getOption(OptionsImpl.ATTRIBUTE_OBF)) {
                accessFlagsLocal = SetFactory.newSet(accessFlagsLocal);
                accessFlagsLocal.remove(AccessFlag.ACC_ENUM);
                accessFlagsLocal.remove(AccessFlag.ACC_SYNTHETIC);
            }
            String prefix = CollectionUtils.join(accessFlagsLocal, " ");
            if (!prefix.isEmpty()) {
                d.keyword(prefix).print(' ');
            }
        }

        if (typeAnnotationsToDump.isEmpty()) {
            d.dump(type);
        } else {
            JavaAnnotatedTypeInstance jah = type.getAnnotatedInstance();
            DecompilerComments comments = new DecompilerComments();
            TypeAnnotationHelper.apply(jah, typeAnnotationsToDump, comments);
            d.dump(comments);
            d.dump(jah);
        }
        d.print(' ').fieldName(name, owner.getClassType(), false, false, true);
    }

    public boolean isAccessibleFrom(JavaRefTypeInstance maybeCaller, ClassFile classFile) {
        return VisibilityHelper.isVisibleTo(maybeCaller, classFile,
                testAccessFlag(AccessFlag.ACC_PUBLIC),
                testAccessFlag(AccessFlag.ACC_PRIVATE),
                testAccessFlag(AccessFlag.ACC_PROTECTED));
    }
}
