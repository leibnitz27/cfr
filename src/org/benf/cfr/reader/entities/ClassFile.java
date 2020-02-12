package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.CodeAnalyserWholeClass;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationAnonymousInner;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.LiteralRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Triplet;
import org.benf.cfr.reader.bytecode.analysis.types.BindingSuperContainer;
import org.benf.cfr.reader.bytecode.analysis.types.BoundSuperCollector;
import org.benf.cfr.reader.bytecode.analysis.types.ClassSignature;
import org.benf.cfr.reader.bytecode.analysis.types.FormalTypeParameter;
import org.benf.cfr.reader.bytecode.analysis.types.GenericTypeBinder;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeAnnotationHelper;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableNamerDefault;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.attributes.Attribute;
import org.benf.cfr.reader.entities.attributes.AttributeBootstrapMethods;
import org.benf.cfr.reader.entities.attributes.AttributeEnclosingMethod;
import org.benf.cfr.reader.entities.attributes.AttributeInnerClasses;
import org.benf.cfr.reader.entities.attributes.AttributeMap;
import org.benf.cfr.reader.entities.attributes.AttributeModule;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeSignature;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationEntryValue;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationTargetInfo;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumper;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumperAnnotation;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumperInterface;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumperModule;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumperNormal;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumperRecord;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryClass;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryNameAndType;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryUTF8;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolUtils;
import org.benf.cfr.reader.entities.innerclass.InnerClassAttributeInfo;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.relationship.MemberNameResolver;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.InnerClassTypeUsageInformation;
import org.benf.cfr.reader.state.OverloadMethodSetCache;
import org.benf.cfr.reader.state.TypeUsageCollectingDumper;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.functors.UnaryProcedure;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.IllegalIdentifierReplacement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClassFile implements Dumpable, TypeUsageCollectable {
    // Constants
    private static final long OFFSET_OF_MAGIC = 0;
    private static final long OFFSET_OF_MINOR = 4;
    private static final long OFFSET_OF_MAJOR = 6;
    private static final long OFFSET_OF_CONSTANT_POOL_COUNT = 8;
    private static final long OFFSET_OF_CONSTANT_POOL = 10;
    // From there on, we have to make up the offsets as we go, as the structure
    // is variable.

    private final ConstantPool constantPool;
    private final Set<AccessFlag> accessFlags;
    private final List<ClassFileField> fields;
    private Map<String, Map<JavaTypeInstance, ClassFileField>> fieldsByName; // Lazily populated if interrogated.

    private final List<Method> methods;
    private Map<String, List<Method>> methodsByName; // Lazily populated if interrogated.
    private final boolean isInnerClass;
    private final Map<JavaTypeInstance, Pair<InnerClassAttributeInfo, ClassFile>> innerClassesByTypeInfo; // populated if analysed.

    private final AttributeMap attributes;
    private final ConstantPoolEntryClass thisClass;
    @SuppressWarnings("FieldCanBeLocal")
    private final ConstantPoolEntryClass rawSuperClass;
    @SuppressWarnings("FieldCanBeLocal")
    private final List<ConstantPoolEntryClass> rawInterfaces;
    private final ClassSignature classSignature;
    private ClassFileVersion classFileVersion;
    private DecompilerComments decompilerComments;

    private boolean begunAnalysis;

    /*
     * If this class represents a generated structure (like a switch lookup table)
     * we will mark it as hidden, and not normally show it.
     *
     * TODO:  TMI.
     */
    private boolean hiddenInnerClass;

    private BindingSuperContainer boundSuperClasses;

    private ClassFileDumper dumpHelper;

    private final String usePath;

    /*
     * Be sure to call loadInnerClasses directly after.
     */
    public ClassFile(final ByteData data, final String usePath, final DCCommonState dcCommonState) {
        this.usePath = usePath;
        Options options = dcCommonState.getOptions();

        int magic = data.getS4At(OFFSET_OF_MAGIC);
        if (magic != 0xCAFEBABE) throw new ConfusedCFRException("Magic != Cafebabe for class file '" + usePath + "'");

        // Members
        int minorVer = data.getU2At(OFFSET_OF_MINOR);
        int majorVer = data.getU2At(OFFSET_OF_MAJOR);
        ClassFileVersion classFileVersion = new ClassFileVersion(majorVer, minorVer);
        this.classFileVersion = classFileVersion;
        final ClassFileVersion cfv = classFileVersion;
        int constantPoolCount = data.getU2At(OFFSET_OF_CONSTANT_POOL_COUNT);
        this.constantPool = new ConstantPool(this, dcCommonState, data.getOffsetData(OFFSET_OF_CONSTANT_POOL), constantPoolCount);
        final long OFFSET_OF_ACCESS_FLAGS = OFFSET_OF_CONSTANT_POOL + constantPool.getRawByteLength();
        final long OFFSET_OF_THIS_CLASS = OFFSET_OF_ACCESS_FLAGS + 2;
        final long OFFSET_OF_SUPER_CLASS = OFFSET_OF_THIS_CLASS + 2;
        final long OFFSET_OF_INTERFACES_COUNT = OFFSET_OF_SUPER_CLASS + 2;
        final long OFFSET_OF_INTERFACES = OFFSET_OF_INTERFACES_COUNT + 2;

        int numInterfaces = data.getU2At(OFFSET_OF_INTERFACES_COUNT);
        ArrayList<ConstantPoolEntryClass> tmpInterfaces = new ArrayList<ConstantPoolEntryClass>();
        ContiguousEntityFactory.buildSized(data.getOffsetData(OFFSET_OF_INTERFACES), (short)numInterfaces, 2, tmpInterfaces,
                new UnaryFunction<ByteData, ConstantPoolEntryClass>() {
                    @Override
                    public ConstantPoolEntryClass invoke(ByteData arg) {
                        int offset = arg.getU2At(0);
                        return (ConstantPoolEntryClass) constantPool.getEntry(offset);
                    }
                }
        );
        thisClass = (ConstantPoolEntryClass) constantPool.getEntry(data.getU2At(OFFSET_OF_THIS_CLASS));

        this.rawInterfaces = tmpInterfaces;

        accessFlags = AccessFlag.build(data.getU2At(OFFSET_OF_ACCESS_FLAGS));

        final long OFFSET_OF_FIELDS_COUNT = OFFSET_OF_INTERFACES + 2 * numInterfaces;
        final long OFFSET_OF_FIELDS = OFFSET_OF_FIELDS_COUNT + 2;
        final int numFields = data.getU2At(OFFSET_OF_FIELDS_COUNT);
        List<Field> tmpFields = ListFactory.newList();
        final long fieldsLength = ContiguousEntityFactory.build(data.getOffsetData(OFFSET_OF_FIELDS), numFields, tmpFields,
                new UnaryFunction<ByteData, Field>() {
                    @Override
                    public Field invoke(ByteData arg) {
                        return new Field(arg, constantPool, cfv);
                    }
                });
        this.fields = ListFactory.newList();
        LiteralRewriter rewriter = new LiteralRewriter(this.getClassType());
        for (Field tmpField : tmpFields) {
            fields.add(new ClassFileField(tmpField, rewriter));
        }

        final long OFFSET_OF_METHODS_COUNT = OFFSET_OF_FIELDS + fieldsLength;
        final long OFFSET_OF_METHODS = OFFSET_OF_METHODS_COUNT + 2;
        final int numMethods = data.getU2At(OFFSET_OF_METHODS_COUNT);
        List<Method> tmpMethods = new ArrayList<Method>(numMethods);
        final long methodsLength = ContiguousEntityFactory.build(data.getOffsetData(OFFSET_OF_METHODS), numMethods, tmpMethods,
                new UnaryFunction<ByteData, Method>() {
                    @Override
                    public Method invoke(ByteData arg) {
                        return new Method(arg, ClassFile.this, constantPool, dcCommonState, cfv);
                    }
                });
//        tmpMethods = MethodOrdering.sort(tmpMethods);
        if (accessFlags.contains(AccessFlag.ACC_STRICT)) {
            for (Method method : tmpMethods) {
                method.getAccessFlags().remove(AccessFlagMethod.ACC_STRICT);
            }
        }

        if (!options.getOption(OptionsImpl.RENAME_ILLEGAL_IDENTS)) {
            for (Method method : tmpMethods) {
                if (IllegalIdentifierReplacement.isIllegalMethodName(method.getName())) {
                    addComment(DecompilerComment.ILLEGAL_IDENTIFIERS);
                    break;
                }
            }
        }

        final long OFFSET_OF_ATTRIBUTES_COUNT = OFFSET_OF_METHODS + methodsLength;
        final long OFFSET_OF_ATTRIBUTES = OFFSET_OF_ATTRIBUTES_COUNT + 2;
        final int numAttributes = data.getU2At(OFFSET_OF_ATTRIBUTES_COUNT);
        ArrayList<Attribute> tmpAttributes = new ArrayList<Attribute>();
        tmpAttributes.ensureCapacity(numAttributes);
        ContiguousEntityFactory.build(data.getOffsetData(OFFSET_OF_ATTRIBUTES), numAttributes, tmpAttributes,
                AttributeFactory.getBuilder(constantPool, classFileVersion)
        );

        this.attributes = new AttributeMap(tmpAttributes);
        AccessFlag.applyAttributes(attributes, accessFlags);
        this.isInnerClass = testIsInnerClass(dcCommonState);

        int superClassIndex = data.getU2At(OFFSET_OF_SUPER_CLASS);
        if (superClassIndex == 0) {
            rawSuperClass = null;
        } else {
            rawSuperClass = (ConstantPoolEntryClass) constantPool.getEntry(superClassIndex);
        }
        this.classSignature = getSignature(constantPool, rawSuperClass, rawInterfaces);

        this.methods = tmpMethods;

        // Need to load inner classes asap so we can infer staticness before any analysis
        this.innerClassesByTypeInfo = new LinkedHashMap<JavaTypeInstance, Pair<InnerClassAttributeInfo, ClassFile>>();

        boolean isInterface = accessFlags.contains(AccessFlag.ACC_INTERFACE);
        boolean isAnnotation = accessFlags.contains(AccessFlag.ACC_ANNOTATION);
        boolean isModule = accessFlags.contains(AccessFlag.ACC_MODULE);
        /*
         * Choose a default dump helper.  This may be overwritten.
         */
        if (isInterface) {
            if (isAnnotation) {
                dumpHelper = new ClassFileDumperAnnotation(dcCommonState);
            } else {
                dumpHelper = new ClassFileDumperInterface(dcCommonState);
            }
        } else {
            if (isModule) {
                if (!methods.isEmpty()) {
                    // This shouldn't be possible, but I suspect someone might try it!
                    addComment("Class file marked as module, but has methods! Treated as a class file");
                    isModule = false;
                }
                if (null == attributes.getByName(AttributeModule.ATTRIBUTE_NAME)) {
                    addComment("Class file marked as module, but no module attribute!");
                    isModule = false;
                }
            }
            if (isModule) {
                dumpHelper = new ClassFileDumperModule(dcCommonState);
            } else {
                dumpHelper = new ClassFileDumperNormal(dcCommonState);
            }
        }

        /*
         *
         */
        if (classFileVersion.before(ClassFileVersion.JAVA_6)) {
            boolean hasSignature = false;
            if (null != attributes.getByName(AttributeSignature.ATTRIBUTE_NAME)) hasSignature = true;
            if (!hasSignature) {
                for (Method method : methods) {
                    if (null != method.getSignatureAttribute()) {
                        hasSignature = true;
                        break;
                    }
                }
            }
            if (hasSignature) {
                addComment("This class specifies class file version " + classFileVersion + " but uses Java 6 signatures.  Assumed Java 6.");
                classFileVersion = ClassFileVersion.JAVA_6;
            }
        }
        if (classFileVersion.before(ClassFileVersion.JAVA_1_0)) {
            addComment(new DecompilerComment("Class file version " + classFileVersion + " predates " + ClassFileVersion.JAVA_1_0 + ", recompilation may lose compatibility!"));
        }
        this.classFileVersion = classFileVersion;


        /*
         * If the type instance for this class has decided that it's an inner class, but the class meta data does not
         * believe so, correct the type instance!
         */
        AttributeInnerClasses attributeInnerClasses = attributes.getByName(AttributeInnerClasses.ATTRIBUTE_NAME);
        JavaRefTypeInstance typeInstance = (JavaRefTypeInstance) thisClass.getTypeInstance();
        if (typeInstance.getInnerClassHereInfo().isInnerClass()) {
            checkInnerClassAssumption(attributeInnerClasses, typeInstance, dcCommonState);
        }

        /*
         * If we're NOT renaming duplicate members, do a quick scan to see if any of our methods could benefit from
         * it.
         */
        if (!options.getOption(OptionsImpl.RENAME_DUP_MEMBERS)) {
            if (MemberNameResolver.verifySingleClassNames(this)) {
                addComment(DecompilerComment.RENAME_MEMBERS);
            }
        }

        if (options.getOption(OptionsImpl.ENUM_SUGAR, classFileVersion)) {
            fixConfusingEnumConstructors();
        }

        if (options.getOption(OptionsImpl.ELIDE_SCALA)) {
            elideScala();
        }

        if (constantPool.isDynamicConstants()) {
            addComment(DecompilerComment.DYNAMIC_CONSTANTS);
        }

        if (dcCommonState.getVersionCollisions().contains(getClassType())) {
            addComment(DecompilerComment.MULTI_VERSION);
        }
        DecompilerComment renamedClass = dcCommonState.renamedTypeComment(getClassType().getRawName());
        if (renamedClass != null) {
            addComment(renamedClass);
        }
    }

    /*
     * This is .... really annoying.  Enums always have missing arguments from the front of their constructors
     * which we add and make synthetic.  Except when they don't!!!
     *
     * Only ever seen on enums which derive Enum directly with no generic behaviour.
     *
     * This is a TERRIBLE heuristic for doing this.
     */
    private void fixConfusingEnumConstructors() {
        if (testAccessFlag(AccessFlag.ACC_ENUM) && TypeConstants.ENUM.equals(getBaseClassType())) {
            List<Method> constructors = getConstructors();
            for (Method constructor : constructors) {
                MethodPrototype prototype = constructor.getMethodPrototype();
                prototype.unbreakEnumConstructor();
            }
        }
    }

    /*
     * Bit of tidying - scala contains loads of serialVersionUid and ScalaSignatures - which don't add anything
     * and just make things harder to read.
     *
     * Ideally this would live elsewhere - but most scala functions are implemented as anonymous inners,
     * and that's not caught by the whole class options.
     */
    private void elideScala() {
        try {
            ClassFileField f = getFieldByName(MiscConstants.SCALA_SERIAL_VERSION, RawJavaType.LONG);
            f.markHidden();
        } catch (Exception ignore) {
        }
        // If it's there.  Don't have a flag to hide attributes (should do, really).
        AttributeRuntimeVisibleAnnotations annotations = attributes.getByName(AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME);
        if (annotations != null) {
            annotations.hide(TypeConstants.SCALA_SIGNATURE);
        }
    }

    /*
     * We might have to correct this, if an assumption has been made based on type name.
     */
    private void checkInnerClassAssumption(AttributeInnerClasses attributeInnerClasses, JavaRefTypeInstance typeInstance, DCCommonState state) {
        if (attributeInnerClasses != null) {
            for (InnerClassAttributeInfo innerClassAttributeInfo : attributeInnerClasses.getInnerClassAttributeInfoList()) {
                if (innerClassAttributeInfo.getInnerClassInfo().equals(typeInstance)) {
                    return;
                }
            }
        }
        // If we're handling obfuscation where we've been given inner class info, then that might be more trustworthy.
        if (state.getObfuscationMapping().providesInnerClassInfo()) {
            return;
        }
        /*
         * No - we are NOT an inner class, regardless of what the guessed information says.
         */
        typeInstance.markNotInner();
    }

    public String getUsePath() {
        return usePath;
    }

    public boolean isInterface() {
        return accessFlags.contains(AccessFlag.ACC_INTERFACE);
    }

    public void addComment(DecompilerComment comment) {
        if (decompilerComments == null) decompilerComments = new DecompilerComments();
        decompilerComments.addComment(comment);
    }

    public void addComment(String comment) {
        if (decompilerComments == null) decompilerComments = new DecompilerComments();
        decompilerComments.addComment(comment);
    }

    private void addComment(String comment, Exception e) {
        addComment(new DecompilerComment(comment, e));
    }

    public DecompilerComments getDecompilerComments() {
        return decompilerComments;
    }

    public List<JavaTypeInstance> getAllClassTypes() {
        List<JavaTypeInstance> res = ListFactory.newList();
        getAllClassTypes(res);
        return res;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        if (thisClass != null) {
            collector.collect(thisClass.getTypeInstance());
        }
        collector.collectFrom(classSignature);
        // Collect all fields.
        for (ClassFileField field : fields) {
            collector.collectFrom(field.getField());
            collector.collectFrom(field.getInitialValue());
        }
        collector.collectFrom(methods);
        // Collect the types of all inner classes, then process recursively.
        for (Map.Entry<JavaTypeInstance, Pair<InnerClassAttributeInfo, ClassFile>> innerClassByTypeInfo : innerClassesByTypeInfo.entrySet()) {
            collector.collect(innerClassByTypeInfo.getKey());
            ClassFile innerClassFile = innerClassByTypeInfo.getValue().getSecond();
            innerClassFile.collectTypeUsages(collector);
        }
        collector.collectFrom(dumpHelper);
        collector.collectFrom(attributes.getByName(AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(attributes.getByName(AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME));
    }

    private void getAllClassTypes(List<JavaTypeInstance> tgt) {
        tgt.add(getClassType());
        for (Pair<InnerClassAttributeInfo, ClassFile> pair : innerClassesByTypeInfo.values()) {
            pair.getSecond().getAllClassTypes(tgt);
        }
    }

    public void setDumpHelper(ClassFileDumper dumpHelper) {
        this.dumpHelper = dumpHelper;
    }

    public void markHiddenInnerClass() {
        hiddenInnerClass = true;
    }

    public ClassFileVersion getClassFileVersion() {
        return classFileVersion;
    }

    public boolean isInnerClass() {
        if (isInnerClass) return true;
        // Sloppy - should be sufficient to rely on above.
        if (thisClass == null) return false;
        return thisClass.getTypeInstance().getInnerClassHereInfo().isInnerClass();
    }

    public ConstantPool getConstantPool() {
        return constantPool;
    }

    public boolean testAccessFlag(AccessFlag accessFlag) {
        return accessFlags.contains(accessFlag);
    }

    public boolean hasFormalTypeParameters() {
        List<FormalTypeParameter> formalTypeParameters = classSignature.getFormalTypeParameters();
        return formalTypeParameters != null && !formalTypeParameters.isEmpty();
    }

    public boolean hasField(String name) {
        if (fieldsByName == null) {
            calculateFieldsByName();
        }
        return fieldsByName.containsKey(name);
    }

    public ClassFileField getFieldByName(String name, JavaTypeInstance type) throws NoSuchFieldException {
        if (fieldsByName == null) {
            calculateFieldsByName();
        }
        Map<JavaTypeInstance, ClassFileField> fieldsByType = fieldsByName.get(name);
        if (fieldsByType == null || fieldsByType.isEmpty()) { // can't be empty, but....
            throw new NoSuchFieldException(name);
        }
        ClassFileField field = fieldsByType.get(type);
        if (field == null) {
            // Fall back to the first one.  This is in case of bad type guesswork.
            return fieldsByType.values().iterator().next();
        }
        return field;
    }

    private void calculateFieldsByName() {
        Options options = constantPool.getDCCommonState().getOptions();
        boolean testIllegal = !options.getOption(OptionsImpl.RENAME_ILLEGAL_IDENTS);
        boolean illegal = false;
        fieldsByName = MapFactory.newMap();
        if (testIllegal) {
            for (ClassFileField field : fields) {
                String rawFieldName = field.getRawFieldName();
                // Check illegality on RAW field name, as otherwise unicode is already quoted.
                if (IllegalIdentifierReplacement.isIllegal(rawFieldName)) {
                    illegal = true;
                    break;
                }
            }
        }
        int smallMemberThreshold = options.getOption(OptionsImpl.RENAME_SMALL_MEMBERS);
        boolean renameSmallMembers = smallMemberThreshold > 0;
        for (ClassFileField field : fields) {
            String fieldName = field.getFieldName();
            JavaTypeInstance fieldType = field.getField().getJavaTypeInstance();
            Map<JavaTypeInstance, ClassFileField> perNameMap = fieldsByName.get(fieldName);
            if (perNameMap == null) {
                perNameMap = MapFactory.newOrderedMap();
                fieldsByName.put(fieldName, perNameMap);
            }
            perNameMap.put(fieldType, field);
            if (renameSmallMembers && fieldName.length() <= smallMemberThreshold) {
                field.getField().setDisambiguate();
            }
        }
        boolean warnAmbig = false;
        for (Map<JavaTypeInstance, ClassFileField> typeMap : fieldsByName.values()) {
            if (typeMap.size() > 1) {
                if (constantPool.getDCCommonState().getOptions().getOption(OptionsImpl.RENAME_DUP_MEMBERS)) {
                    for (ClassFileField field : typeMap.values()) {
                        field.getField().setDisambiguate();
                    }
                } else {
                    warnAmbig = true;
                }
            }
        }
        if (warnAmbig) {
            addComment(DecompilerComment.RENAME_MEMBERS);
        }
        if (illegal) {
            addComment(DecompilerComment.ILLEGAL_IDENTIFIERS);
        }
    }

    public List<ClassFileField> getFields() {
        return fields;
    }

    public List<Method> getMethods() {
        return methods;
    }

    private List<Method> getMethodsWithMatchingName(final MethodPrototype prototype) {
        return Functional.filter(methods, new Predicate<Method>() {
            @Override
            public boolean test(Method in) {
                return in.getName().equals(prototype.getName());
            }
        });
    }

    private void collectMethods(MethodPrototype prototype, List<Method> tgt, Set<JavaTypeInstance> seen) {
        tgt.addAll(getMethodsWithMatchingName(prototype));
        if (classSignature == null) return;
        collectTypeMethods(prototype, tgt, seen, classSignature.getSuperClass());
        for (JavaTypeInstance intf : classSignature.getInterfaces()) {
            collectTypeMethods(prototype, tgt, seen, intf);
        }
    }

    private void collectTypeMethods(MethodPrototype prototype, List<Method> tgt, Set<JavaTypeInstance> seen, JavaTypeInstance clazz) {
        if (clazz == null) return;
        clazz = clazz.getDeGenerifiedType();
        if (!(clazz instanceof JavaRefTypeInstance)) return;
        if (!seen.add(clazz)) return;
        ClassFile classFile = constantPool.getDCCommonState().getClassFileOrNull(clazz);
        if (classFile != null) classFile.collectMethods(prototype, tgt, seen);
    }

    public OverloadMethodSet getOverloadMethodSet(MethodPrototype prototype) {
        OverloadMethodSetCache cache = constantPool.getDCCommonState().getOverloadMethodSetCache();
        OverloadMethodSet res = cache.get(this, prototype);
        if (res == null) {
            res = getOverloadMethodSetInner(prototype);
            cache.set(this, prototype, res);
        }
        return res;
    }

    private OverloadMethodSet getOverloadMethodSetInner(final MethodPrototype prototype) {
        final JavaRefTypeInstance thiz = getRefClassType();
        /*
         * Filter this list to find all methods with the name number of args.
         */
        final boolean isInstance = prototype.isInstanceMethod();
        final int numArgs = prototype.getArgs().size();
        List<Method> named = ListFactory.newList();
        collectMethods(prototype, named, SetFactory.<JavaTypeInstance>newIdentitySet());
        final boolean isVarArgs = (prototype.isVarArgs());
        named = Functional.filter(named, new Predicate<Method>() {
            @Override
            public boolean test(Method in) {
                MethodPrototype other = in.getMethodPrototype();
                if (other.isInstanceMethod() != isInstance) return false;
                if (!in.isVisibleTo(thiz)) return false;
                boolean otherIsVarargs = other.isVarArgs();
                if (isVarArgs) {
                    if (otherIsVarargs) return true;
                    return (other.getArgs().size() >= numArgs);
                }
                if (otherIsVarargs) {
                    return (other.getArgs().size() <= numArgs);
                }
                return (other.getArgs().size() == numArgs);
            }
        });
        List<MethodPrototype> prototypes = Functional.map(named, new UnaryFunction<Method, MethodPrototype>() {
            @Override
            public MethodPrototype invoke(Method arg) {
                return arg.getMethodPrototype();
            }
        });
        /*
         * Remove TOTAL duplicates - those with identical toStrings.
         * TODO : Better way?
         *
         * Why does stringBuilder appear to have duplicate methods?
         */
        List<MethodPrototype> out = ListFactory.newList();
        Set<String> matched = SetFactory.newSet();
        out.add(prototype);
        matched.add(prototype.getComparableString());
        for (MethodPrototype other : prototypes) {
            if (matched.add(other.getComparableString())) {
                out.add(other);
            }
        }

        return new OverloadMethodSet(this, prototype, out);
    }


    /* We need to make sure we get the 'correct' method...
     * This requires a pass with a type binder, so that
     *
     * x (Double, int ) matches x (T , int) where T is bound to Double.
     */
    public Method getMethodByPrototype(final MethodPrototype prototype) throws NoSuchMethodException {
        Method methodMatch = getMethodByPrototypeOrNull(prototype);
        if (methodMatch != null) return methodMatch;
        throw new NoSuchMethodException();
    }

    public Method getMethodByPrototypeOrNull(final MethodPrototype prototype) {
        List<Method> named = getMethodsWithMatchingName(prototype);
        Method methodMatch = null;
        for (Method method : named) {
            MethodPrototype tgt = method.getMethodPrototype();
            if (tgt.equalsMatch(prototype)) return method;
            if (tgt.equalsGeneric(prototype)) {
                methodMatch = method;
            }
        }
        return methodMatch;
    }

    private Method getAccessibleMethodByPrototype(final MethodPrototype prototype, GenericTypeBinder binder, JavaRefTypeInstance accessor) throws NoSuchMethodException {
        List<Method> named = getMethodsWithMatchingName(prototype);
        Method methodMatch = null;
        for (Method method : named) {
            if (!method.isVisibleTo(accessor)) continue;
            MethodPrototype tgt = method.getMethodPrototype();
            if (tgt.equalsMatch(prototype)) return method;
            if (binder != null) {
                if (tgt.equalsGeneric(prototype, binder)) {
                    methodMatch = method;
                }
            }
        }
        if (methodMatch != null) return methodMatch;
        throw new NoSuchMethodException();
    }


    public Method getSingleMethodByNameOrNull(String name) {
        List<Method> methodList = getMethodsByNameOrNull(name);
        if (methodList == null || methodList.size() != 1) return null;
        return methodList.get(0);
    }

    public List<Method> getMethodsByNameOrNull(String name) {
        if (methodsByName == null) {
            methodsByName = MapFactory.newMap();
            for (Method method : methods) {
                List<Method> list = methodsByName.get(method.getName());
                if (list == null) {
                    list = ListFactory.newList();
                    methodsByName.put(method.getName(), list);
                }
                list.add(method);
            }
        }
        return methodsByName.get(name);
    }

    public List<Method> getMethodByName(String name) throws NoSuchMethodException {
        List<Method> methods = getMethodsByNameOrNull(name);
        if (methods == null) throw new NoSuchMethodException(name);
        return methods;
    }


    public List<Method> getConstructors() {
        List<Method> res = ListFactory.newList();
        for (Method method : methods) {
            if (method.isConstructor()) res.add(method);
        }
        return res;
    }

    public AttributeBootstrapMethods getBootstrapMethods() {
        return attributes.getByName(AttributeBootstrapMethods.ATTRIBUTE_NAME);
    }

    public ConstantPoolEntryClass getThisClassConstpoolEntry() {
        return thisClass;
    }

    private boolean isInferredAnonymousStatic(DCCommonState state, JavaTypeInstance thisType, JavaTypeInstance innerType) {
        if (!innerType.getInnerClassHereInfo().isAnonymousClass()) return false;

        boolean j8orLater = classFileVersion.equalOrLater(ClassFileVersion.JAVA_8);
        if (!j8orLater) return false;
        
        JavaRefTypeInstance containing = thisType.getInnerClassHereInfo().getOuterClass();

        // if the outer class doesn't exist, we just don't know
        ClassFile containingClassFile = containing.getClassFile();
        if (containingClassFile == null) {
            return false;
        }

        // if the containing class is a static class, so is this.
        if (containingClassFile.getAccessFlags().contains(AccessFlag.ACC_STATIC)) {
            return true;
        }

        AttributeEnclosingMethod encloser = attributes.getByName(AttributeEnclosingMethod.ATTRIBUTE_NAME);
        if (encloser == null) return false;
        int classIndex = encloser.getClassIndex();
        if (classIndex == 0) return false;
        ConstantPoolEntryClass encloserClass = constantPool.getClassEntry(classIndex);
        JavaTypeInstance encloserType = encloserClass.getTypeInstance();
        if (encloserType != containing) return false;
        int methodIndex = encloser.getMethodIndex();
        if (methodIndex == 0) return false;
        ConstantPoolEntryNameAndType nameAndType = constantPool.getNameAndTypeEntry(methodIndex);
        ConstantPoolEntryUTF8 descriptor = nameAndType.getDescriptor();
        String name = nameAndType.getName().getValue();
        VariableNamer fakeNamer = new VariableNamerDefault();

        MethodPrototype basePrototype = ConstantPoolUtils.parseJavaMethodPrototype(state,null, containing, name, /* interfaceMethod */ false, Method.MethodConstructor.NOT, descriptor, constantPool, false /* we can't tell */, false, fakeNamer);

        try {
            Method m = containingClassFile.getMethodByPrototype(basePrototype);
            if (m.getAccessFlags().contains(AccessFlagMethod.ACC_STATIC)) {
                return true;
            }
        } catch (NoSuchMethodException ignore) {
        }
        return false;
    }

    private boolean testIsInnerClass(DCCommonState dcCommonState) {
        List<InnerClassAttributeInfo> innerClassAttributeInfoList = getInnerClassAttributeInfos(dcCommonState);
        if (innerClassAttributeInfoList == null) return false;
        final JavaTypeInstance thisType = thisClass.getTypeInstance();

        for (InnerClassAttributeInfo innerClassAttributeInfo : innerClassAttributeInfoList) {
            JavaTypeInstance innerType = innerClassAttributeInfo.getInnerClassInfo();
            if (innerType == thisType) return true;
        }
        return false;
    }

    // just after construction
    public void loadInnerClasses(DCCommonState dcCommonState) {
        List<InnerClassAttributeInfo> innerClassAttributeInfoList = getInnerClassAttributeInfos(dcCommonState);
        if (innerClassAttributeInfoList == null) return;

        final JavaTypeInstance thisType = thisClass.getTypeInstance();

        for (InnerClassAttributeInfo innerClassAttributeInfo : innerClassAttributeInfoList) {
            JavaTypeInstance innerType = innerClassAttributeInfo.getInnerClassInfo();
            if (innerType == null) continue;

            if (innerType == thisType) {
                accessFlags.addAll(innerClassAttributeInfo.getAccessFlags());
                // But if it's J9+, we're crippled by https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8034044
                // which removes the static marker on anonymous classes.
                // Why am I checking j8+? BECAUSE java 9.0.4 SAYS J8. FFS.
                if (!accessFlags.contains(AccessFlag.ACC_STATIC)) {
                    if (isInferredAnonymousStatic(dcCommonState, thisType, innerType)) {
                        accessFlags.add(AccessFlag.ACC_STATIC);
                    }
                }
                // A public class can be marked as protected "as an inner class".
                sanitiseAccessPermissions();
            }

            /*
             * Inner classes can be referred to when they are not direct inner classes.
             * We even refer to inner classes which belong to entirely different classes!
             */
            if (!innerType.getInnerClassHereInfo().isInnerClassOf(thisType)) continue;

            /* If we're loading inner classes, then we definitely want to recursively apply that
             */
            try {
                ClassFile innerClass = dcCommonState.getClassFile(innerType);
                innerClass.loadInnerClasses(dcCommonState);
                // This is a fallback mechanism incase the access flags above aren't working - do we need it?
//                markInnerClassAsStatic(options, innerClass, thisType);

                innerClassesByTypeInfo.put(innerType, new Pair<InnerClassAttributeInfo, ClassFile>(innerClassAttributeInfo, innerClass));
            } catch (CannotLoadClassException ignore) {
            }

        }
    }

    private List<InnerClassAttributeInfo> getInnerClassAttributeInfos(DCCommonState state) {
        AttributeInnerClasses attributeInnerClasses = attributes.getByName(AttributeInnerClasses.ATTRIBUTE_NAME);
        List<InnerClassAttributeInfo> innerClassAttributeInfoList = attributeInnerClasses == null ? null : attributeInnerClasses.getInnerClassAttributeInfoList();
        if (innerClassAttributeInfoList != null) {
            return innerClassAttributeInfoList;
        }
        // If we don't have any inner class attributes, we might be dealing with obfuscated code that has stripped them.
        return state.getObfuscationMapping().getInnerClassInfo(getClassType());
    }

    private void analyseInnerClassesPass1(DCCommonState state) {
        if (innerClassesByTypeInfo == null) return;
        for (Pair<InnerClassAttributeInfo, ClassFile> innerClassInfoClassFilePair : innerClassesByTypeInfo.values()) {
            ClassFile classFile = innerClassInfoClassFilePair.getSecond();
            classFile.analyseMid(state);
        }
    }

    private void analysePassOuterFirst(UnaryProcedure<ClassFile> fn) {
        try {
            fn.call(this);
        } catch (RuntimeException e) {
            addComment("Exception performing whole class analysis ignored.", e);
        }

        if (innerClassesByTypeInfo == null) return;
        for (Pair<InnerClassAttributeInfo, ClassFile> innerClassInfoClassFilePair : innerClassesByTypeInfo.values()) {
            ClassFile classFile = innerClassInfoClassFilePair.getSecond();
            classFile.analysePassOuterFirst(fn);
        }
    }

    public void analyseTop(final DCCommonState dcCommonState, final TypeUsageCollectingDumper typeUsageCollectingDumper) {
        analyseMid(dcCommonState);
        analysePassOuterFirst(new UnaryProcedure<ClassFile>() {
            @Override
            public void call(ClassFile arg) {
                CodeAnalyserWholeClass.wholeClassAnalysisPass2(arg, dcCommonState);
            }
        });
        /*
         * Perform a pass to determine what imports / classes etc we used / failed.
         */
        this.dump(typeUsageCollectingDumper);
        analysePassOuterFirst(new UnaryProcedure<ClassFile>() {
            @Override
            public void call(ClassFile arg) {
                CodeAnalyserWholeClass.wholeClassAnalysisPass3(arg, dcCommonState, typeUsageCollectingDumper);
            }
        });
    }

    private void analyseSyntheticTags(Method method, Options options) {
        try {
            Op04StructuredStatement code = method.getAnalysis();
            if (code == null) return;
            if (options.getOption(OptionsImpl.REWRITE_TRY_RESOURCES, getClassFileVersion())) {
                boolean isResourceRelease = Op04StructuredStatement.isTryWithResourceSynthetic(method, code);
                if (isResourceRelease) {
                    method.getAccessFlags().add(AccessFlagMethod.ACC_FAKE_END_RESOURCE);
                }
            }
        } catch (Exception e) {
            // ignore.
        }
    }

    private void analyseOverrides() {
        try {
            BindingSuperContainer bindingSuperContainer = getBindingSupers();
            Map<JavaRefTypeInstance, JavaGenericRefTypeInstance> boundSupers = bindingSuperContainer.getBoundSuperClasses();
            List<Triplet<JavaRefTypeInstance, ClassFile, GenericTypeBinder>> bindTesters = ListFactory.newList();
            for (Map.Entry<JavaRefTypeInstance, JavaGenericRefTypeInstance> entry : boundSupers.entrySet()) {
                JavaRefTypeInstance superC = entry.getKey();
                if (superC.equals(getClassType())) continue;
                ClassFile superClsFile = null;
                try {
                    superClsFile = superC.getClassFile();
                } catch (CannotLoadClassException ignore) {
                }
                if (superClsFile == null) continue;
                if (superClsFile == this) continue; // shouldn't happen.

                JavaGenericRefTypeInstance boundSuperC = entry.getValue();
                GenericTypeBinder binder = null;
                if (boundSuperC != null) {
                    binder = superClsFile.getGenericTypeBinder(boundSuperC);
                }

                bindTesters.add(Triplet.make(superC, superClsFile, binder));
            }


            for (Method method : methods) {
                if (method.isConstructor()) continue;
                if (method.testAccessFlag(AccessFlagMethod.ACC_STATIC)) continue;
                MethodPrototype prototype = method.getMethodPrototype();
                Method baseMethod = null;
                for (Triplet<JavaRefTypeInstance, ClassFile, GenericTypeBinder> bindTester : bindTesters) {
                    ClassFile classFile = bindTester.getSecond();
                    GenericTypeBinder genericTypeBinder = bindTester.getThird();
                    try {
                        baseMethod = classFile.getAccessibleMethodByPrototype(prototype, genericTypeBinder, (JavaRefTypeInstance)getClassType().getDeGenerifiedType());
                    } catch (NoSuchMethodException ignore) {
                    }
                    if (baseMethod != null) break;
                }
                if (baseMethod != null) method.markOverride();
            }
        } catch (RuntimeException e) {
            addComment("Failed to analyse overrides", e);
        }
    }


    private void analyseMid(DCCommonState state) {
        Options options = state.getOptions();
        if (this.begunAnalysis) {
            return;
        }
        this.begunAnalysis = true;
        /*
         * Analyse inner classes first, so we know if they're static when we reference them
         * from the outer class.
         */
        if (options.getOption(OptionsImpl.DECOMPILE_INNER_CLASSES)) {
            analyseInnerClassesPass1(state);
        }

        /*
         * While we're going to consider the methods in order, analyse synthetic methods first
         * so that we can tag them.
         */
        Pair<List<Method>, List<Method>> partition = Functional.partition(methods, new Predicate<Method>() {
            @Override
            public boolean test(Method x) {
                return x.getAccessFlags().contains(AccessFlagMethod.ACC_SYNTHETIC);
            }
        });
        // Analyse synthetic methods.
        for (Method method : partition.getFirst()) {
            method.analyse();
            analyseSyntheticTags(method, options);
        }
        // Non synthetics.
        for (Method method : partition.getSecond()) {
            method.analyse();
        }

        try {
            if (options.getOption(OptionsImpl.OVERRIDES, classFileVersion)) {
                analyseOverrides();
            }

            CodeAnalyserWholeClass.wholeClassAnalysisPass1(this, state);
        } catch (RuntimeException e) {
            addComment(DecompilerComment.WHOLE_CLASS_EXCEPTION);
        }

    }

    public void releaseCode() {
        if (isInnerClass) return;
        for (Method method : methods) {
            method.releaseCode();
        }
    }

    public JavaTypeInstance getClassType() {
        return thisClass.getTypeInstance();
    }

    public JavaRefTypeInstance getRefClassType() {
        return (JavaRefTypeInstance)thisClass.getTypeInstance();
    }

    public JavaTypeInstance getBaseClassType() {
        return classSignature.getSuperClass();
    }

    public ClassSignature getClassSignature() {
        return classSignature;
    }

    public Set<AccessFlag> getAccessFlags() {
        return accessFlags;
    }

    private void sanitiseAccessPermissions() {
        if (accessFlags.contains(AccessFlag.ACC_PRIVATE)) {
            accessFlags.remove(AccessFlag.ACC_PROTECTED);
            accessFlags.remove(AccessFlag.ACC_PUBLIC);
        } else if (accessFlags.contains(AccessFlag.ACC_PROTECTED)) {
            accessFlags.remove(AccessFlag.ACC_PUBLIC);
        }
    }

    private ClassSignature getSignature(ConstantPool cp,
                                        ConstantPoolEntryClass rawSuperClass,
                                        List<ConstantPoolEntryClass> rawInterfaces) {
        AttributeSignature signatureAttribute = attributes.getByName(AttributeSignature.ATTRIBUTE_NAME);

        if (signatureAttribute != null) {
            try {
                return ConstantPoolUtils.parseClassSignature(signatureAttribute.getSignature(), cp);
            } catch (Exception ignore) {
                // Corrupt?
            }
        }
        // If the class isn't generic (or has had the attribute removed, or corrupted), we have to use the
        // runtime type info.
        List<JavaTypeInstance> interfaces = ListFactory.newList();
        for (ConstantPoolEntryClass rawInterface : rawInterfaces) {
            interfaces.add(rawInterface.getTypeInstance());
        }

        return new ClassSignature(null,
                rawSuperClass == null ? null : rawSuperClass.getTypeInstance(),
                interfaces);

    }


    public void dumpNamedInnerClasses(Dumper d) {
        if (innerClassesByTypeInfo == null || innerClassesByTypeInfo.isEmpty()) return;

        for (Pair<InnerClassAttributeInfo, ClassFile> innerClassEntry : innerClassesByTypeInfo.values()) {
            // catchy!
            InnerClassInfo innerClassInfo = innerClassEntry.getFirst().getInnerClassInfo().getInnerClassHereInfo();
            if (innerClassInfo.isSyntheticFriendClass()) {
                continue;
            }
            if (innerClassInfo.isMethodScopedClass()) {
                continue;
            }
            ClassFile classFile = innerClassEntry.getSecond();
            if (classFile.hiddenInnerClass) {
                continue;
            }
            TypeUsageInformation typeUsageInformation = d.getTypeUsageInformation();
            TypeUsageInformation innerclassTypeUsageInformation = new InnerClassTypeUsageInformation(typeUsageInformation, (JavaRefTypeInstance) classFile.getClassType());
            d.newln();
            Dumper d2 = d.withTypeUsageInformation(innerclassTypeUsageInformation);
            classFile.dumpHelper.dump(classFile, ClassFileDumper.InnerClassDumpType.INNER_CLASS, d2);
        }
    }

    @Override
    public Dumper dump(Dumper d) {
        return dumpHelper.dump(this, ClassFileDumper.InnerClassDumpType.NOT, d);
    }

    public Dumper dumpAsInlineClass(Dumper d) {
        return dumpHelper.dump(this, ClassFileDumper.InnerClassDumpType.INLINE_CLASS, d);
    }

    public String getFilePath() {
        return thisClass.getFilePath();
    }

    @Override
    public String toString() {
        return thisClass.getTextPath();
    }


    private static void getFormalParametersText(ClassSignature signature, TypeAnnotationHelper ah,
                                                UnaryFunction<Integer, Predicate<AnnotationTableTypeEntry>> preFact,
                                                UnaryFunction<Integer, Predicate<AnnotationTableTypeEntry>> innerFact,
                                                Dumper d) {
        List<FormalTypeParameter> formalTypeParameters = signature.getFormalTypeParameters();
        if (formalTypeParameters == null || formalTypeParameters.isEmpty()) return;
        d.separator("<");
        boolean first = true;
        for (int idx=0,len=formalTypeParameters.size();idx<len;idx++) {
            FormalTypeParameter formalTypeParameter = formalTypeParameters.get(idx);
            first = StringUtils.comma(first, d);
            if (ah != null) {
                List<AnnotationTableTypeEntry> pre = Functional.filter(ah.getEntries(), preFact.invoke(idx));
                List<AnnotationTableTypeEntry> inner = Functional.filter(ah.getEntries(), innerFact.invoke(idx));
                if (!(pre.isEmpty() && inner.isEmpty())) {
                    // TODO : preprocess - don't do this at dumper.
                    formalTypeParameter.dump(d, pre, inner);
                    continue;
                }
            }
            formalTypeParameter.dump(d);
        }
        d.print(">");
    }

    public void dumpReceiverClassIdentity(List<AnnotationTableTypeEntry> recieverAnnotations, Dumper d) {
        Pair<List<AnnotationTableTypeEntry>, List<AnnotationTableTypeEntry>> split = Functional.partition(recieverAnnotations, new Predicate<AnnotationTableTypeEntry>() {
            @Override
            public boolean test(AnnotationTableTypeEntry in) {
                return in.getTypePath().segments.isEmpty();
            }
        });
        List<AnnotationTableTypeEntry> pre = split.getFirst();
        List<AnnotationTableTypeEntry> type = split.getSecond();
        if (!pre.isEmpty()) {
            pre.get(0).dump(d);
            d.print(" ");
        }
        JavaTypeInstance t = classSignature.getThisGeneralTypeClass(this.getClassType(), constantPool);
        JavaAnnotatedTypeInstance jat = t.getAnnotatedInstance();
        TypeAnnotationHelper.apply(jat, type, null, new DecompilerComments());
        d.dump(jat);
    }

    public void dumpClassIdentity(Dumper d) {
        d.dump(getThisClassConstpoolEntry().getTypeInstance());
        TypeAnnotationHelper typeAnnotations = TypeAnnotationHelper.create(attributes,
                TypeAnnotationEntryValue.type_generic_class_interface,
                TypeAnnotationEntryValue.type_type_parameter_class_interface);
        UnaryFunction<Integer, Predicate<AnnotationTableTypeEntry>> outerFact = new UnaryFunction<Integer, Predicate<AnnotationTableTypeEntry>>() {
            @Override
            public Predicate<AnnotationTableTypeEntry> invoke(final Integer arg) {
                return new Predicate<AnnotationTableTypeEntry>() {
                    @Override
                    public boolean test(AnnotationTableTypeEntry in) {
                        if (in.getValue() != TypeAnnotationEntryValue.type_generic_class_interface) return false;
                        // TODO : Cleaner way pls!
                        return ((TypeAnnotationTargetInfo.TypeAnnotationParameterTarget) in.getTargetInfo()).getIndex() == arg;
                    }
                };
            }
        };
        UnaryFunction<Integer, Predicate<AnnotationTableTypeEntry>> innerFact = new UnaryFunction<Integer, Predicate<AnnotationTableTypeEntry>>() {
            @Override
            public Predicate<AnnotationTableTypeEntry> invoke(final Integer arg) {
                return new Predicate<AnnotationTableTypeEntry>() {
                    @Override
                    public boolean test(AnnotationTableTypeEntry in) {
                        if (in.getValue() != TypeAnnotationEntryValue.type_type_parameter_class_interface) return false;
                        return ((TypeAnnotationTargetInfo.TypeAnnotationParameterBoundTarget) in.getTargetInfo()).getIndex() == arg;
                    }
                };
            }
        };
        getFormalParametersText(getClassSignature(), typeAnnotations, outerFact, innerFact, d);
    }

    /*
     * Go from a bound instance of this class to a bound instance of the super class.  superType is currently partially unbound.
     */
    public BindingSuperContainer getBindingSupers() {
        // Start with the generic version of this type, i.e. if this is Fred<X>

        if (boundSuperClasses == null) {
            boundSuperClasses = generateBoundSuperClasses();
        }
        return boundSuperClasses;
    }

    private BindingSuperContainer generateBoundSuperClasses() {
        BoundSuperCollector boundSuperCollector = new BoundSuperCollector(this);

        JavaTypeInstance thisType = getClassSignature().getThisGeneralTypeClass(getClassType(), getConstantPool());

        GenericTypeBinder genericTypeBinder;
        if (thisType instanceof JavaGenericRefTypeInstance) {
            JavaGenericRefTypeInstance genericThisType = (JavaGenericRefTypeInstance) thisType;
            genericTypeBinder = GenericTypeBinder.buildIdentityBindings(genericThisType);
            boundSuperCollector.collect(genericThisType, BindingSuperContainer.Route.IDENTITY);
        } else {
            genericTypeBinder = null;
            boundSuperCollector.collect((JavaRefTypeInstance) thisType, BindingSuperContainer.Route.IDENTITY);
        }

        JavaTypeInstance base = classSignature.getSuperClass();
        if (base == null) return new BindingSuperContainer(this, new HashMap<JavaRefTypeInstance, JavaGenericRefTypeInstance>(), new HashMap<JavaRefTypeInstance, BindingSuperContainer.Route>());
        getBoundSuperClasses2(base, genericTypeBinder, boundSuperCollector, BindingSuperContainer.Route.EXTENSION, SetFactory.<JavaTypeInstance>newSet());
        for (JavaTypeInstance interfaceBase : classSignature.getInterfaces()) {
            getBoundSuperClasses2(interfaceBase, genericTypeBinder, boundSuperCollector, BindingSuperContainer.Route.INTERFACE, SetFactory.<JavaTypeInstance>newSet());
        }

        return boundSuperCollector.getBoundSupers();
    }

    private void getBoundSuperClasses(JavaTypeInstance boundGeneric, BoundSuperCollector boundSuperCollector, BindingSuperContainer.Route route, Set<JavaTypeInstance> seen) {
        // TODO: This seems deeply over complicated ;)
        // Perhaps rather than matching in terms of types, we could match in terms of the signature?
        JavaTypeInstance thisType = getClassSignature().getThisGeneralTypeClass(getClassType(), getConstantPool());
        /*
         * Work out the mapping between the unbound version and boundGeneric, then apply those mappings to
         * the superclass / interfaces.
         *
         * genericThisType is the unbound generic version of this type class.
         *
         * i.e. boundGeneric    is Fred<String>
         *      genericThisType is Fred<X>
         *
         * Now, given that our class signature will say
         *
         * Fred<X> extends HashMap<X, X> implements Joe<X, Double>
         *
         * we can create boundGeneric instances for HashMap and Joe, and repeat the process.
         */

        GenericTypeBinder genericTypeBinder;
        if (!(thisType instanceof JavaGenericRefTypeInstance)) {
            genericTypeBinder = null;
        } else {
            JavaGenericRefTypeInstance genericThisType = (JavaGenericRefTypeInstance) thisType;

            if (boundGeneric instanceof JavaGenericRefTypeInstance) {
                genericTypeBinder = GenericTypeBinder.extractBindings(genericThisType, boundGeneric);
            } else {
                genericTypeBinder = null;
            }
        }
        /*
         * Now, apply this to each of our superclass/interfaces.
         */
        JavaTypeInstance base = classSignature.getSuperClass();
        if (base == null) return;
        getBoundSuperClasses2(base, genericTypeBinder, boundSuperCollector, route, SetFactory.newSet(seen));
        for (JavaTypeInstance interfaceBase : classSignature.getInterfaces()) {
            getBoundSuperClasses2(interfaceBase, genericTypeBinder, boundSuperCollector, BindingSuperContainer.Route.INTERFACE, SetFactory.newSet(seen));
        }
    }

    public GenericTypeBinder getGenericTypeBinder(JavaGenericRefTypeInstance boundGeneric) {
        JavaTypeInstance thisType = getClassSignature().getThisGeneralTypeClass(getClassType(), getConstantPool());
        if (!(thisType instanceof JavaGenericRefTypeInstance)) {
            return null;
        } else {
            JavaGenericRefTypeInstance genericThisType = (JavaGenericRefTypeInstance) thisType;
            return GenericTypeBinder.extractBindings(genericThisType, boundGeneric);
        }
    }

    private void getBoundSuperClasses2(JavaTypeInstance base, GenericTypeBinder genericTypeBinder, BoundSuperCollector boundSuperCollector, BindingSuperContainer.Route route,
                                       Set<JavaTypeInstance> seen) {
        if (seen.contains(base)) return;
        seen.add(base);

        if (base instanceof JavaRefTypeInstance) {
            // No bindings to do, can't go any further, mark relationship and move on.
            boundSuperCollector.collect((JavaRefTypeInstance) base, route);
            ClassFile classFile = ((JavaRefTypeInstance) base).getClassFile();
            if (classFile != null) classFile.getBoundSuperClasses(base, boundSuperCollector, route, seen);
            return;
        }

        if (!(base instanceof JavaGenericRefTypeInstance)) {
            throw new IllegalStateException("Base class is not generic");
        }
        JavaGenericRefTypeInstance genericBase = (JavaGenericRefTypeInstance) base;
        /*
         * Create a bound version of this, based on the bindings we have earlier.
         */
        JavaGenericRefTypeInstance boundBase = genericBase.getBoundInstance(genericTypeBinder);

        boundSuperCollector.collect(boundBase, route);
        /*
         * And recurse.
         */
        ClassFile classFile = null;
        try {
            classFile = genericBase.getDeGenerifiedType().getClassFile();
        } catch (CannotLoadClassException ignore) {
        }
        if (classFile == null) {
            return;
        }
        classFile.getBoundSuperClasses(boundBase, boundSuperCollector, route, seen);
    }

    private List<ConstructorInvokationAnonymousInner> anonymousUsages = ListFactory.newList();

    private List<ConstructorInvokationSimple> methodUsages = ListFactory.newList();

    public void noteAnonymousUse(ConstructorInvokationAnonymousInner anoynmousInner) {
        anonymousUsages.add(anoynmousInner);
    }

    public void noteMethodUse(ConstructorInvokationSimple constructorCall) {
        methodUsages.add(constructorCall);
    }

    public List<ConstructorInvokationAnonymousInner> getAnonymousUsages() {
        return anonymousUsages;
    }

    public List<ConstructorInvokationSimple> getMethodUsages() {
        return methodUsages;
    }

    // More of an extension method really :)
    public static JavaTypeInstance getAnonymousTypeBase(ClassFile classFile) {
        ClassSignature signature = classFile.getClassSignature();
        if (signature.getInterfaces().isEmpty()) {
            return signature.getSuperClass();
        } else {
            return signature.getInterfaces().get(0);
        }
    }

    public AttributeMap getAttributes() {
        return attributes;
    }
}
