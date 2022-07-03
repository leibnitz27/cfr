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
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.KnowsRawSize;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
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

    private JavaTypeInstance generateTypeInstance() {
        AttributeSignature sig = getSignatureAttribute();
        ConstantPoolEntryUTF8 signature = sig == null ? null : sig.getSignature();
        ConstantPoolEntryUTF8 descriptor = cp.getUTF8Entry(descriptorIndex);
        JavaTypeInstance sigType = null;
        JavaTypeInstance desType;
        if (signature != null) {
            try {
                sigType = ConstantPoolUtils.decodeTypeTok(signature.getValue(), cp);
                if (sigType != null) {
                    // TODO : Ideally, we wouldn't return early here, and we would verify the signature type against
                    // the descriptor type.  But that means we need to have a generic type binder for the field.
                    // That's ok, but inner classes make this quite expensive.  For now, return.
                    return sigType;
                }
            } catch (Exception e) {
                // ignore.
            }
        }


        try {
            desType =  ConstantPoolUtils.decodeTypeTok(descriptor.getValue(), cp);
        } catch (RuntimeException e) {
            if (sigType == null) {
                throw e;
            }
            desType = sigType;
        }

        if (sigType != null) {
            if (sigType.getDeGenerifiedType().equals(desType.getDeGenerifiedType())) {
                return sigType;
            } else {
                // someone lied....
                // TODO : This might not be a lie if the field is a generic placeholder.
                return desType;
            }
        } else {
            return desType;
        }
    }

    public JavaTypeInstance getJavaTypeInstance() {
        if (cachedDecodedType == null) {
            cachedDecodedType = generateTypeInstance();
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
        collector.collectFromT(attributes.getByName(AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME));
        collector.collectFromT(attributes.getByName(AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME));
        collector.collectFromT(attributes.getByName(AttributeRuntimeVisibleTypeAnnotations.ATTRIBUTE_NAME));
        collector.collectFromT(attributes.getByName(AttributeRuntimeInvisibleTypeAnnotations.ATTRIBUTE_NAME));
    }

    private List<AnnotationTableEntry> getRecordComponentAnnotations(ClassFile owner, List<AnnotationTableEntry> fieldAnnotations) {
        // Note: Don't have to consider TYPE_USE annotations because they are implicitly propagated
        // to field and already dumped as part of regular field annotation handling
        
        List<AnnotationTableEntry> componentAnnotations = null;

        AttributeRecord recordAttribute = owner.getAttributes().getByName(AttributeRecord.ATTRIBUTE_NAME);
        List<Attribute> componentAttributes;
        if (recordAttribute != null && (componentAttributes = recordAttribute.getRecordComponentAttributes(fieldName)) != null) {
            componentAnnotations = MiscAnnotations.BasicAnnotations(new AttributeMap(componentAttributes));
        }

        if (componentAnnotations == null) {
            // Return all annotations which were propagated from component to field
            return fieldAnnotations;
        } else if (fieldAnnotations == null) {
            return componentAnnotations;
        } else {
            // Create copy because original list might not be modifiable
            componentAnnotations = ListFactory.newList(componentAnnotations);

            // First collect the type of all annotations with target RECORD_COMPONENT
            Set<JavaTypeInstance> componentAnnotationTypes = Functional.mapToSet(componentAnnotations, new UnaryFunction<AnnotationTableEntry, JavaTypeInstance>() {
                @Override
                public JavaTypeInstance invoke(AnnotationTableEntry arg) {
                    return arg.getClazz();
                }
            });

            // Then consider all annotations without RECORD_COMPONENT target, but with FIELD
            // target which are implicitly propagated from component to field
            // Annotations which are already in componentAnnotationTypes have both RECORD_COMPONENT
            // and FIELD as target and therefore don't have to be added a second time

            // Note: METHOD annotations propagated from component don't have to be considered;
            // RecordRewriter will not hide the accessor method if it has annotations

            for (AnnotationTableEntry fieldAnnotation : fieldAnnotations) {
                if (!componentAnnotationTypes.contains(fieldAnnotation.getClazz())) {
                    componentAnnotations.add(fieldAnnotation);
                }
            }

            return componentAnnotations;
        }
    }

    public void dump(Dumper d, String name, ClassFile owner, boolean asRecordField) {
        JavaTypeInstance type = getJavaTypeInstance();

        List<AnnotationTableEntry> declarationAnnotations = MiscAnnotations.BasicAnnotations(attributes);
        // If field is backing a record component, get annotations for component as well
        if (asRecordField) {
            declarationAnnotations = getRecordComponentAnnotations(owner, declarationAnnotations);
        }
        TypeAnnotationHelper tah = TypeAnnotationHelper.create(attributes, TypeAnnotationEntryValue.type_field_or_record_component);
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
