package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.variables.Ident;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableNamerFactory;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.DeclarationAnnotationHelper.DeclarationAnnotationsInfo;
import org.benf.cfr.reader.entities.annotations.AnnotationTableEntry;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.annotations.ElementValue;
import org.benf.cfr.reader.entities.attributes.*;
import org.benf.cfr.reader.entities.classfilehelpers.VisibilityHelper;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryClass;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryUTF8;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolUtils;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.LocalClassAwareTypeUsageInformation;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.collections.CollectionUtils;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.TypeContext;

import java.util.*;

/* Too much in common with field - refactor.
 *
 */

public class Method implements KnowsRawSize, TypeUsageCollectable {

    public enum MethodConstructor {
        NOT(false, false),
        STATIC_CONSTRUCTOR(false, false),
        CONSTRUCTOR(true, false),
        RECORD_CANONICAL_CONSTRUCTOR(true, false),
        ENUM_CONSTRUCTOR(true, true),
        // Eclipse enums behave like normal enums, except they declare arguments.
        ECLIPSE_ENUM_CONSTRUCTOR(true, true);

        private final boolean isConstructor;
        private final boolean isEnumConstructor;

        MethodConstructor(boolean isConstructor, boolean isEnumConstructor) {
            this.isConstructor = isConstructor;
            this.isEnumConstructor = isEnumConstructor;
        }

        public boolean isConstructor() {
            return isConstructor;
        }

        public boolean isEnumConstructor() {
            return isEnumConstructor;
        }
    }

    public enum Visibility
    {
        Visible,
        HiddenSynthetic,
        HiddenBridge,
        HiddenDeadCode // dead, or pointless.
    }

    private static final long OFFSET_OF_ACCESS_FLAGS = 0;
    private static final long OFFSET_OF_NAME_INDEX = 2;
    private static final long OFFSET_OF_DESCRIPTOR_INDEX = 4;
    private static final long OFFSET_OF_ATTRIBUTES_COUNT = 6;
    private static final long OFFSET_OF_ATTRIBUTES = 8;

    private static final AnnotationTableEntry OVERRIDE_ANNOTATION = new AnnotationTableEntry(TypeConstants.OVERRIDE, Collections.<String, ElementValue>emptyMap());

    private final long length;
    private final EnumSet<AccessFlagMethod> accessFlags;
    private final AttributeMap attributes;
    private MethodConstructor isConstructor;
    private final int descriptorIndex;
    private final AttributeCode codeAttribute;
    private final ConstantPool cp;
    private final VariableNamer variableNamer;
    private final MethodPrototype methodPrototype;
    private final ClassFile classFile;
    private Visibility hidden;
    private DecompilerComments comments;
    private final Map<JavaRefTypeInstance, String> localClasses = MapFactory.newOrderedMap();
    private boolean isOverride;
    private transient Set<JavaTypeInstance> thrownTypes = null;

    public Method(ByteData raw, ClassFile classFile, final ConstantPool cp, final DCCommonState dcCommonState, final ClassFileVersion classFileVersion) {
        Options options = dcCommonState.getOptions();

        this.cp = cp;
        this.classFile = classFile;
        this.accessFlags = AccessFlagMethod.build(raw.getU2At(OFFSET_OF_ACCESS_FLAGS));
        this.descriptorIndex = raw.getU2At(OFFSET_OF_DESCRIPTOR_INDEX);
        this.hidden = Visibility.Visible;
        int nameIndex = raw.getU2At(OFFSET_OF_NAME_INDEX);
        String initialName = cp.getUTF8Entry(nameIndex).getValue();

        int numAttributes = raw.getU2At(OFFSET_OF_ATTRIBUTES_COUNT);
        ArrayList<Attribute> tmpAttributes = new ArrayList<Attribute>();
        tmpAttributes.ensureCapacity(numAttributes);
        long attributesLength = ContiguousEntityFactory.build(raw.getOffsetData(OFFSET_OF_ATTRIBUTES), numAttributes, tmpAttributes,
                AttributeFactory.getBuilder(cp, classFileVersion));

        this.attributes = new AttributeMap(tmpAttributes);
        AccessFlagMethod.applyAttributes(attributes, accessFlags);
        this.length = OFFSET_OF_ATTRIBUTES + attributesLength;

        MethodConstructor methodConstructor = MethodConstructor.NOT;
        if (initialName.equals(MiscConstants.INIT_METHOD)) {
            boolean isEnum = classFile.getAccessFlags().contains(AccessFlag.ACC_ENUM);
            methodConstructor = isEnum ? MethodConstructor.ENUM_CONSTRUCTOR : MethodConstructor.CONSTRUCTOR;
        } else if (initialName.equals(MiscConstants.STATIC_INIT_METHOD)) {
            methodConstructor = MethodConstructor.STATIC_CONSTRUCTOR;

            // JVM Spec 2nd ed., chapter 4.6: All access flags except static for class initializers are ignored
            // Pre java 7 class files even allow static initializers to be non-static
            // See classFileParser.cpp#parse_method
            accessFlags.clear();
            accessFlags.add(AccessFlagMethod.ACC_STATIC);
        }
        this.isConstructor = methodConstructor;
        if (methodConstructor.isConstructor() && accessFlags.contains(AccessFlagMethod.ACC_STRICT)) {
            accessFlags.remove(AccessFlagMethod.ACC_STRICT);
            classFile.getAccessFlags().add(AccessFlag.ACC_STRICT);
        }

        AttributeCode codeAttribute = attributes.getByName(AttributeCode.ATTRIBUTE_NAME);
        if (codeAttribute == null) {
            // Because we don't have a code attribute, we don't have a local variable table.
            this.variableNamer = VariableNamerFactory.getNamer(null, cp);
            this.codeAttribute = null;
        } else {
            this.codeAttribute = codeAttribute;
            AttributeLocalVariableTable variableTable = options.getOption(OptionsImpl.USE_NAME_TABLE) ? this.codeAttribute.getLocalVariableTable() : null;
            this.variableNamer = VariableNamerFactory.getNamer(variableTable, cp);
            // This rigamarole is neccessary because we don't provide the factory for the code attribute enough information
            // to get the Method (this).
            this.codeAttribute.setMethod(this);
        }
        this.methodPrototype = generateMethodPrototype(options, initialName, methodConstructor);
        if (accessFlags.contains(AccessFlagMethod.ACC_BRIDGE) &&
                // javac only ever generates bridges into instances,
                // however kotlin loses useful info if we hide them.
                !accessFlags.contains(AccessFlagMethod.ACC_STATIC) &&
                options.getOption(OptionsImpl.HIDE_BRIDGE_METHODS)) {
            this.hidden = Visibility.HiddenBridge;
        }
    }

    void releaseCode() {
        if (codeAttribute != null) {
            codeAttribute.releaseCode();
        }
        attributes.clear();
    }

    // While a method might (according to the descriptor and code) look like it can
    // be elided, we might have annotations which need to be emitted.
    // https://github.com/leibnitz27/cfr/issues/93
    public boolean hasDumpableAttributes() {
        return attributes.any(
                AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME,
                AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME,
                AttributeRuntimeVisibleTypeAnnotations.ATTRIBUTE_NAME,
                AttributeRuntimeInvisibleTypeAnnotations.ATTRIBUTE_NAME,
                AttributeRuntimeVisibleParameterAnnotations.ATTRIBUTE_NAME,
                AttributeRuntimeInvisibleParameterAnnotations.ATTRIBUTE_NAME
                );
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        methodPrototype.collectTypeUsages(collector);
        collector.collectFrom(attributes.getByName(AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(attributes.getByName(AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(attributes.getByName(AttributeRuntimeVisibleTypeAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(attributes.getByName(AttributeRuntimeInvisibleTypeAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(attributes.getByName(AttributeRuntimeVisibleParameterAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(attributes.getByName(AttributeRuntimeInvisibleParameterAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(attributes.getByName(AttributeAnnotationDefault.ATTRIBUTE_NAME));
        if (codeAttribute != null) {
            codeAttribute.collectTypeUsages(collector);
            codeAttribute.analyse().collectTypeUsages(collector);
        }
        collector.collect(localClasses.keySet());
        collector.collectFrom(attributes.getByName(AttributeExceptions.ATTRIBUTE_NAME));
    }

    public boolean copyLocalClassesFrom(Method other) {
        for (Map.Entry<JavaRefTypeInstance, String> entry : other.localClasses.entrySet()) {
            markUsedLocalClassType(entry.getKey(), entry.getValue());
        }
        return !other.localClasses.isEmpty();
    }

    public Set<AccessFlagMethod> getAccessFlags() {
        return accessFlags;
    }

    public void hideSynthetic() {
        this.hidden = Visibility.HiddenSynthetic;
    }

    public void hideDead() {
        this.hidden = Visibility.HiddenDeadCode;
    }

    public Visibility hiddenState() {
        return hidden;
    }

    public boolean testAccessFlag(AccessFlagMethod flag) {
        return accessFlags.contains(flag);
    }

    public MethodConstructor getConstructorFlag() {
        return isConstructor;
    }

    public void setConstructorFlag(MethodConstructor flag) {
        isConstructor = flag;
    }

    AttributeSignature getSignatureAttribute() {
        return attributes.getByName(AttributeSignature.ATTRIBUTE_NAME);
    }

    public VariableNamer getVariableNamer() {
        return variableNamer;
    }

    public ClassFile getClassFile() {
        return classFile;
    }

    @Override
    public long getRawByteLength() {
        return length;
    }

    public String getName() {
        return methodPrototype.getName();
    }

    /* This is a bit ugly - otherwise though we need to tie a variable namer to this earlier.
     * We can't always use the signature... in an enum, for example, it lies!
     *
     * Method  : <init> name : 30, descriptor 31
     * Descriptor ConstantUTF8[(Ljava/lang/String;I)V]
     * Signature Signature:ConstantUTF8[()V]
     */
    private MethodPrototype generateMethodPrototype(Options options, String initialName, MethodConstructor constructorFlag) {
        AttributeSignature sig = options.getOption(OptionsImpl.USE_SIGNATURES) ?getSignatureAttribute() : null;
        ConstantPoolEntryUTF8 signature = sig == null ? null : sig.getSignature();
        ConstantPoolEntryUTF8 descriptor = cp.getUTF8Entry(descriptorIndex);
        boolean isInstance = !accessFlags.contains(AccessFlagMethod.ACC_STATIC);
        boolean isVarargs = accessFlags.contains(AccessFlagMethod.ACC_VARARGS);
        boolean isSynthetic = accessFlags.contains(AccessFlagMethod.ACC_SYNTHETIC);
        DCCommonState state = cp.getDCCommonState();
        MethodPrototype sigproto = null;
        MethodPrototype desproto;
        boolean isEnum = constructorFlag == MethodConstructor.ENUM_CONSTRUCTOR;
        if (signature == null) {
            // This is 'fun'.  Eclipse doesn't provide a signature, and its descriptor is honest.
            // java does, which means that javac's signature disagrees with the descriptor.
            if (isEnum) {
                constructorFlag = MethodConstructor.ECLIPSE_ENUM_CONSTRUCTOR;
            }
        }
        if (signature != null) {
            try {
                sigproto = ConstantPoolUtils.parseJavaMethodPrototype(state, classFile, classFile.getClassType(), initialName, isInstance, constructorFlag, signature, cp, isVarargs, isSynthetic, variableNamer);
            } catch (MalformedPrototypeException e) {
                // deliberately empty.
            }
        }
        try {
            desproto = ConstantPoolUtils.parseJavaMethodPrototype(state, classFile, classFile.getClassType(), initialName, isInstance, constructorFlag, descriptor, cp, isVarargs, isSynthetic, variableNamer);
        } catch (MalformedPrototypeException e) {
            if (sigproto == null) throw e;
            // this shouln't be possible, but we might be able to handle it.
            desproto = sigproto;
        }
        /*
         * Work around bug in inner class signatures.
         *
         * https://stackoverflow.com/questions/15131040/java-inner-class-inconsistency-between-descriptor-and-signature-attribute-clas
         */
        if (classFile.isInnerClass() && sigproto != null) {
            if (desproto.getArgs().size() != sigproto.getArgs().size()) {
                // error due to inner class sig bug.
                fixupInnerClassSignature(desproto, sigproto);
            }
        }
        if (sigproto == null) {
            return desproto;
        }
        if (checkSigProto(desproto, sigproto, isEnum, classFile.isInnerClass() && constructorFlag.isConstructor())) {
            return sigproto;
        } else {
            addComment(DecompilerComment.BAD_SIGNATURE);
            return desproto;
        }
    }

    private static boolean checkSigProto(MethodPrototype desproto, MethodPrototype sigproto, boolean isEnumConstructor, boolean isInnerConstructor) {
        if (sigproto == null) return false;

        List<JavaTypeInstance> desargs = desproto.getArgs();
        List<JavaTypeInstance> sigargs = sigproto.getArgs();

        int offset = 0;
        if (desargs.size() != sigargs.size()) {
            if (isInnerConstructor || isEnumConstructor) {
                // anonymous inner classes will not map correctly unless we can infer
                return true;
            } else {
                return false;
            }
        }
        for (int x=0,len=sigargs.size();x<len;++x) {
            JavaTypeInstance desarg = desargs.get(x+offset).getArrayStrippedType();
            JavaTypeInstance sigarg = sigargs.get(x).getArrayStrippedType();
            if (sigarg instanceof JavaGenericPlaceholderTypeInstance) {
                // This could be a stronger check.
                continue;
            }
            if (!sigarg.getDeGenerifiedType().equals(desarg.getDeGenerifiedType())) {
                return false;
            }
        }
        return true;
    }

    private static void fixupInnerClassSignature(MethodPrototype descriptor, MethodPrototype signature) {
        List<JavaTypeInstance> descriptorArgs = descriptor.getArgs();
        List<JavaTypeInstance> signatureArgs = signature.getArgs();
        if (signatureArgs.size() != descriptorArgs.size() - 1) {
            // It's not the known issue, can't really deal with it.
            signature.setDescriptorProto(descriptor);
            return;
        }
        for (int x = 0; x < signatureArgs.size(); ++x) {
            if (!descriptorArgs.get(x + 1).equals(signatureArgs.get(x).getDeGenerifiedType())) {
                // Incompatible.
                return;
            }
        }
        // Ok.  We've fallen foul of the bad signature-on-inner-class
        // compiler bug.  Patch up the inner class signature so that it takes the implicit
        // outer this pointer.
        // Since we've got the ref to the mutable signatureArgs, let's be DISGUSTING and mutate that.
        signatureArgs.add(0, descriptorArgs.get(0));
    }

    public MethodPrototype getMethodPrototype() {
        return methodPrototype;
    }

    void markOverride() {
        isOverride = true;
    }

    public void markUsedLocalClassType(JavaTypeInstance javaTypeInstance, String suggestedName) {
        javaTypeInstance = javaTypeInstance.getDeGenerifiedType();
        if (!(javaTypeInstance instanceof JavaRefTypeInstance))
            throw new IllegalStateException("Bad local class Type " + javaTypeInstance.getRawName());
        localClasses.put((JavaRefTypeInstance) javaTypeInstance, suggestedName);
    }

    public void markUsedLocalClassType(JavaTypeInstance javaTypeInstance) {
        markUsedLocalClassType(javaTypeInstance, null);
    }

    private void dumpMethodAnnotations(Dumper d, List<AnnotationTableEntry> nullableDeclAnnotations) {
        // Explicitly dump override first.
        if (isOverride) {
            OVERRIDE_ANNOTATION.dump(d).newln();
        }

        if (nullableDeclAnnotations != null) {
            for (AnnotationTableEntry annotation : nullableDeclAnnotations) {
                annotation.dump(d).newln();
            }
        }
    }

    // We can get the declared thrown types from an attribute, or from the signature.
    // Of course, the signature can lie.
    private List<JavaTypeInstance> getDeclaredThrownTypes() {
        List<JavaTypeInstance> attributeTypes = getAttributeDeclaredThrownTypes();
        List<JavaTypeInstance> prototypeExceptionTypes = methodPrototype.getExceptionTypes();
        // trust the attributes before the signature.
        int len = attributeTypes.size();
        if (len != prototypeExceptionTypes.size()) return attributeTypes;
        List<JavaTypeInstance> boundProtoExceptionTypes = methodPrototype.getSignatureBoundExceptions();

        for (int x=0; x<len; ++x) {
            JavaTypeInstance signatureType = boundProtoExceptionTypes.get(x);
            JavaTypeInstance attributeType = attributeTypes.get(x);
            // be VERY paranoid.
            if (!attributeType.equals(signatureType)) {
                return attributeTypes;
            }
        }
        return prototypeExceptionTypes;
    }

    private List<JavaTypeInstance> getAttributeDeclaredThrownTypes() {
        AttributeExceptions exceptionsAttribute = attributes.getByName(AttributeExceptions.ATTRIBUTE_NAME);
        if (exceptionsAttribute != null) {
            return Functional.map(exceptionsAttribute.getExceptionClassList(), new UnaryFunction<ConstantPoolEntryClass, JavaTypeInstance>() {
                @Override
                public JavaTypeInstance invoke(ConstantPoolEntryClass arg) {
                    return arg.getTypeInstance();
                }
            });
        }
        else {
            return Collections.emptyList();
        }
    }

    public Set<JavaTypeInstance> getThrownTypes() {
        if (thrownTypes == null) {
            thrownTypes = new LinkedHashSet<JavaTypeInstance>(getDeclaredThrownTypes());
        }
        return thrownTypes;
    }

    private void dumpSignatureText(boolean asClass, Dumper d) {
        MethodPrototypeAnnotationsHelper annotationsHelper = new MethodPrototypeAnnotationsHelper(attributes);
        JavaTypeInstance nullableReturnType = getMethodPrototype().getReturnType();
        DeclarationAnnotationsInfo annotationsInfo = DeclarationAnnotationHelper.getDeclarationInfo(nullableReturnType, annotationsHelper.getMethodAnnotations(), annotationsHelper.getMethodReturnAnnotations());
        /*
         * TODO: This is incorrect, but currently cannot easily influence whether the dumped type is admissible
         * Therefore assume it is always admissible unless required not to
         * (even though then the dumped type might still be admissible)
         */
        boolean usesAdmissibleType = !annotationsInfo.requiresNonAdmissibleType();
        dumpMethodAnnotations(d, annotationsInfo.getDeclarationAnnotations(usesAdmissibleType));

        EnumSet<AccessFlagMethod> localAccessFlags = SetFactory.newSet(accessFlags);
        if (!asClass) {
            if (codeAttribute != null && !accessFlags.contains(AccessFlagMethod.ACC_STATIC)
                    && !accessFlags.contains(AccessFlagMethod.ACC_PRIVATE)) {
                d.keyword("default ");
            }
            // Dumping as interface.
            localAccessFlags.remove(AccessFlagMethod.ACC_ABSTRACT);
        }
        localAccessFlags.remove(AccessFlagMethod.ACC_VARARGS);
        String prefix = CollectionUtils.join(localAccessFlags, " ");

        if (!prefix.isEmpty()) d.keyword(prefix);

        if (isConstructor == MethodConstructor.STATIC_CONSTRUCTOR) {
            return;
        }

        if (!prefix.isEmpty()) d.print(' ');

        String displayName = isConstructor.isConstructor() ?
                d.getTypeUsageInformation().getName(classFile.getClassType(), TypeContext.None) :
                methodPrototype.getFixedName();

        getMethodPrototype().dumpDeclarationSignature(d, displayName, isConstructor, annotationsHelper, annotationsInfo.getTypeAnnotations(usesAdmissibleType));
        AttributeExceptions exceptionsAttribute = attributes.getByName(AttributeExceptions.ATTRIBUTE_NAME);
        if (exceptionsAttribute != null) {
            List<AnnotationTableTypeEntry> att = annotationsHelper.getTypeTargetAnnotations(TypeAnnotationEntryValue.type_throws);
            d.print(" throws ");
            boolean first = true;
            int idx = -1;
            for (JavaTypeInstance typeInstance : getDeclaredThrownTypes()) {
                ++idx;
                first = StringUtils.comma(first, d);
                if (att != null) {
                    JavaAnnotatedTypeInstance jat = typeInstance.getAnnotatedInstance();
                    final int sidx = idx;
                    List<AnnotationTableTypeEntry> exceptionAnnotations = Functional.filter(att, new Predicate<AnnotationTableTypeEntry>() {
                        @Override
                        public boolean test(AnnotationTableTypeEntry in) {
                            return sidx == ((TypeAnnotationTargetInfo.TypeAnnotationThrowsTarget)in.getTargetInfo()).getIndex();
                        }
                    });
                    DecompilerComments comments = new DecompilerComments();
                    TypeAnnotationHelper.apply(jat, exceptionAnnotations, comments);
                    d.dump(comments);
                    d.dump(jat);
                } else {
                    d.dump(typeInstance);
                }
            }
        }
    }

    public Op04StructuredStatement getAnalysis() {
        if (codeAttribute == null) throw new ConfusedCFRException("No code in this method to analyze");
        Op04StructuredStatement analysis = codeAttribute.analyse();
        return analysis;
    }

    public boolean isConstructor() {
        return isConstructor.isConstructor();
    }

    void analyse() {
        try {
            if (codeAttribute != null) {
                codeAttribute.analyse();
            }
            if (!methodPrototype.parametersComputed()) {
                /*
                 * No code attribute - we still need to assign variable names.
                 */
                Map<Integer, Ident> identMap = MapFactory.newLazyMap(new UnaryFunction<Integer, Ident>() {
                    @Override
                    public Ident invoke(Integer arg) {
                        return new Ident(arg, 0);
                    }
                });
                methodPrototype.computeParameters(getConstructorFlag(), identMap);
            }
        } catch (RuntimeException e) {
            System.out.println("While processing method : " + this.getName());
            throw e;
        }
    }

    public boolean hasCodeAttribute() {
        return codeAttribute != null;
    }

    public AttributeCode getCodeAttribute() {
        return codeAttribute;
    }

    private void dumpComments(Dumper d) {
        if (comments != null) {
            comments.dump(d);

            for (DecompilerComment decompilerComment : comments.getCommentCollection()) {
                String string = decompilerComment.getSummaryMessage();
                if (string != null) {
                    d.addSummaryError(this, string);
                }
            }
        }
    }

    public void setComments(DecompilerComments comments) {
        if (this.comments == null) {
            this.comments = comments;
        } else {
            this.comments.addComments(comments.getCommentCollection());
        }
    }

    private void addComment(DecompilerComment comment) {
        if (comments == null) {
            comments = new DecompilerComments();
        }
        comments.addComment(comment);
    }

    public boolean isVisibleTo(JavaRefTypeInstance maybeCaller) {
        return VisibilityHelper.isVisibleTo(maybeCaller, getClassFile(),
                accessFlags.contains(AccessFlagMethod.ACC_PUBLIC),
                accessFlags.contains(AccessFlagMethod.ACC_PRIVATE),
                accessFlags.contains(AccessFlagMethod.ACC_PROTECTED)
        );
    }

    public void dump(Dumper d, boolean asClass) {
        if (codeAttribute != null) {
            // force analysis so we have comments.
            codeAttribute.analyse();
        }
        dumpComments(d);
        dumpSignatureText(asClass, d);
        if (codeAttribute == null) {
            AttributeAnnotationDefault annotationDefault = attributes.getByName(AttributeAnnotationDefault.ATTRIBUTE_NAME);
            if (annotationDefault != null) {
                JavaTypeInstance resultType = methodPrototype.getReturnType();
                d.keyword(" default ").dump(annotationDefault.getElementValue().withTypeHint(resultType));
            }
            d.endCodeln();
        } else {
            /*
             * Override the dumper with a proxy which makes sure that local classes defined here are 'better'.
             */
            if (!localClasses.isEmpty()) {
                TypeUsageInformation tui = d.getTypeUsageInformation();
                Map<JavaRefTypeInstance, String> filteredLocalClasses = MapFactory.newMap();
                // We may have better information already though (see InnerClassTest31).
                for (Map.Entry<JavaRefTypeInstance, String> entry : localClasses.entrySet()) {
                    if (!(entry.getValue() == null && tui.hasLocalInstance(entry.getKey()))) {
                        filteredLocalClasses.put(entry.getKey(), entry.getValue());
                    }
                }
                if (!filteredLocalClasses.isEmpty()) {
                    TypeUsageInformation overrides = new LocalClassAwareTypeUsageInformation(filteredLocalClasses, d.getTypeUsageInformation());
                    d = d.withTypeUsageInformation(overrides);
                }
            }
            d.print(' ').dump(codeAttribute);
        }
    }

    @Override
    public String toString() {
        return getName() + ": " + methodPrototype;
    }
}
