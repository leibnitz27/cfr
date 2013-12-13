package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.CodeAnalyserWholeClass;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationAnoynmousInner;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Triplet;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.entities.attributes.*;
import org.benf.cfr.reader.entities.classfilehelpers.*;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryClass;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolUtils;
import org.benf.cfr.reader.entities.innerclass.InnerClassAttributeInfo;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 18:25
 * To change this template use File | Settings | File Templates.
 */
public class ClassFile implements Dumpable, TypeUsageCollectable {
    // Constants
    private final long OFFSET_OF_MAGIC = 0;
    private final long OFFSET_OF_MINOR = 4;
    private final long OFFSET_OF_MAJOR = 6;
    private final long OFFSET_OF_CONSTANT_POOL_COUNT = 8;
    private final long OFFSET_OF_CONSTANT_POOL = 10;
    // From there on, we have to make up the offsets as we go, as the structure
    // is variable.


    // Members
    private final short minorVer;
    private final short majorVer;
    private final ConstantPool constantPool;
    private final Set<AccessFlag> accessFlags;
    private final List<ClassFileField> fields;
    private Map<String, ClassFileField> fieldsByName; // Lazily populated if interrogated.

    private final List<Method> methods;
    private Map<String, List<Method>> methodsByName; // Lazily populated if interrogated.

    private final Map<JavaTypeInstance, Pair<InnerClassAttributeInfo, ClassFile>> innerClassesByTypeInfo; // populated if analysed.


    private final Map<String, Attribute> attributes;
    private final ConstantPoolEntryClass thisClass;
    private final ConstantPoolEntryClass rawSuperClass;
    private final List<ConstantPoolEntryClass> rawInterfaces;
    private final ClassSignature classSignature;
    private final ClassFileVersion classFileVersion;
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

        int magic = data.getS4At(OFFSET_OF_MAGIC);
        if (magic != 0xCAFEBABE) throw new ConfusedCFRException("Magic != Cafebabe");

        minorVer = data.getS2At(OFFSET_OF_MINOR);
        majorVer = data.getS2At(OFFSET_OF_MAJOR);
        short constantPoolCount = data.getS2At(OFFSET_OF_CONSTANT_POOL_COUNT);
        this.constantPool = new ConstantPool(this, dcCommonState, data.getOffsetData(OFFSET_OF_CONSTANT_POOL), constantPoolCount);
        final long OFFSET_OF_ACCESS_FLAGS = OFFSET_OF_CONSTANT_POOL + constantPool.getRawByteLength();
        final long OFFSET_OF_THIS_CLASS = OFFSET_OF_ACCESS_FLAGS + 2;
        final long OFFSET_OF_SUPER_CLASS = OFFSET_OF_THIS_CLASS + 2;
        final long OFFSET_OF_INTERFACES_COUNT = OFFSET_OF_SUPER_CLASS + 2;
        final long OFFSET_OF_INTERFACES = OFFSET_OF_INTERFACES_COUNT + 2;

        short numInterfaces = data.getS2At(OFFSET_OF_INTERFACES_COUNT);
        ArrayList<ConstantPoolEntryClass> tmpInterfaces = new ArrayList<ConstantPoolEntryClass>();
        final long interfacesLength = ContiguousEntityFactory.buildSized(data.getOffsetData(OFFSET_OF_INTERFACES), numInterfaces, 2, tmpInterfaces,
                new UnaryFunction<ByteData, ConstantPoolEntryClass>() {
                    @Override
                    public ConstantPoolEntryClass invoke(ByteData arg) {
                        return (ConstantPoolEntryClass) constantPool.getEntry(arg.getS2At(0));
                    }
                }
        );
        thisClass = (ConstantPoolEntryClass) constantPool.getEntry(data.getS2At(OFFSET_OF_THIS_CLASS));

//        if (configCallback != null) {
//            configCallback.configureWith(this);
//        }
        this.rawInterfaces = tmpInterfaces;


        accessFlags = AccessFlag.build(data.getS2At(OFFSET_OF_ACCESS_FLAGS));

        final long OFFSET_OF_FIELDS_COUNT = OFFSET_OF_INTERFACES + 2 * numInterfaces;
        final long OFFSET_OF_FIELDS = OFFSET_OF_FIELDS_COUNT + 2;
        final short numFields = data.getS2At(OFFSET_OF_FIELDS_COUNT);
        List<Field> tmpFields = ListFactory.newList();
        final long fieldsLength = ContiguousEntityFactory.build(data.getOffsetData(OFFSET_OF_FIELDS), numFields, tmpFields,
                new UnaryFunction<ByteData, Field>() {
                    @Override
                    public Field invoke(ByteData arg) {
                        return new Field(arg, constantPool);
                    }
                });
        this.fields = ListFactory.newList();
        for (Field tmpField : tmpFields) {
            fields.add(new ClassFileField(tmpField));
        }

        final long OFFSET_OF_METHODS_COUNT = OFFSET_OF_FIELDS + fieldsLength;
        final long OFFSET_OF_METHODS = OFFSET_OF_METHODS_COUNT + 2;
        final short numMethods = data.getS2At(OFFSET_OF_METHODS_COUNT);
        ArrayList<Method> tmpMethods = new ArrayList<Method>();
        tmpMethods.ensureCapacity(numMethods);
        final long methodsLength = ContiguousEntityFactory.build(data.getOffsetData(OFFSET_OF_METHODS), numMethods, tmpMethods,
                new UnaryFunction<ByteData, Method>() {
                    @Override
                    public Method invoke(ByteData arg) {
                        return new Method(arg, ClassFile.this, constantPool, dcCommonState);
                    }
                });
        this.methods = tmpMethods;
        if (accessFlags.contains(AccessFlag.ACC_STRICT)) {
            for (Method method : tmpMethods) {
                method.getAccessFlags().remove(AccessFlagMethod.ACC_STRICT);
            }
        }

        final long OFFSET_OF_ATTRIBUTES_COUNT = OFFSET_OF_METHODS + methodsLength;
        final long OFFSET_OF_ATTRIBUTES = OFFSET_OF_ATTRIBUTES_COUNT + 2;
        final short numAttributes = data.getS2At(OFFSET_OF_ATTRIBUTES_COUNT);
        ArrayList<Attribute> tmpAttributes = new ArrayList<Attribute>();
        tmpAttributes.ensureCapacity(numAttributes);
        ContiguousEntityFactory.build(data.getOffsetData(OFFSET_OF_ATTRIBUTES), numAttributes, tmpAttributes,
                new UnaryFunction<ByteData, Attribute>() {
                    @Override
                    public Attribute invoke(ByteData arg) {
                        return AttributeFactory.build(arg, constantPool);
                    }
                });
        this.attributes = ContiguousEntityFactory.addToMap(new HashMap<String, Attribute>(), tmpAttributes);

        short superClassIndex = data.getS2At(OFFSET_OF_SUPER_CLASS);
        if (superClassIndex == 0) {
            rawSuperClass = null;
        } else {
            rawSuperClass = (ConstantPoolEntryClass) constantPool.getEntry(superClassIndex);
        }
        this.classSignature = getSignature(constantPool, rawSuperClass, rawInterfaces);

        // Need to load inner classes asap so we can infer staticness before any analysis
        this.innerClassesByTypeInfo = new LinkedHashMap<JavaTypeInstance, Pair<InnerClassAttributeInfo, ClassFile>>();

        boolean isInterface = accessFlags.contains(AccessFlag.ACC_INTERFACE);
        boolean isAnnotation = accessFlags.contains(AccessFlag.ACC_ANNOTATION);
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
            dumpHelper = new ClassFileDumperNormal(dcCommonState);
        }

        /*
         *
         */
        ClassFileVersion classFileVersion = new ClassFileVersion(majorVer, minorVer);
        if (classFileVersion.before(ClassFileVersion.JAVA_6)) {
            boolean hasSignature = false;
            if (null != getAttributeByName(AttributeSignature.ATTRIBUTE_NAME)) hasSignature = true;
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

        this.classFileVersion = classFileVersion;

    }

    public String getUsePath() {
        return usePath;
    }

    private void addComment(String comment) {
        if (decompilerComments == null) decompilerComments = new DecompilerComments();
        decompilerComments.addComment(comment);
    }

    private void addComment(String comment, Exception e) {
        if (decompilerComments == null) decompilerComments = new DecompilerComments();
        decompilerComments.addComment(new DecompilerComment(comment, e));
    }

    /* Get the list of constantPools, and that of inner classes */
    public List<ConstantPool> getAllCps() {
        Set<ConstantPool> res = SetFactory.newSet();
        getAllCps(res);
        return ListFactory.newList(res);
    }

    private void getAllCps(Set<ConstantPool> tgt) {
        tgt.add(constantPool);
        for (Pair<InnerClassAttributeInfo, ClassFile> pair : innerClassesByTypeInfo.values()) {
            pair.getSecond().getAllCps(tgt);
        }
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
            collector.collect(field.getField().getJavaTypeInstance(constantPool));
            collector.collectFrom(field.getInitialValue());
        }
        collector.collectFrom(methods);
        // Collect the types of all inner classes, then process recursively.
        for (Map.Entry<JavaTypeInstance, Pair<InnerClassAttributeInfo, ClassFile>> innerClassByTypeInfo : innerClassesByTypeInfo.entrySet()) {
            collector.collect(innerClassByTypeInfo.getKey());
            ClassFile innerClassFile = innerClassByTypeInfo.getValue().getSecond();
            innerClassFile.collectTypeUsages(collector);
        }
        collector.collectFrom(getAttributeByName(AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME));
        collector.collectFrom(getAttributeByName(AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME));
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
        if (thisClass == null) return false;
        return thisClass.getTypeInstance().getInnerClassHereInfo().isInnerClass();
    }

    public ConstantPool getConstantPool() {
        return constantPool;
    }

    public boolean testAccessFlag(AccessFlag accessFlag) {
        return accessFlags.contains(accessFlag);
    }

    private void markAsStatic() {
        accessFlags.add(AccessFlag.ACC_STATIC);
    }

    public boolean hasFormalTypeParameters() {
        List<FormalTypeParameter> formalTypeParameters = classSignature.getFormalTypeParameters();
        return formalTypeParameters != null && !formalTypeParameters.isEmpty();
    }


    public ClassFileField getFieldByName(String name) throws NoSuchFieldException {
        if (fieldsByName == null) {
            fieldsByName = MapFactory.newMap();
            for (ClassFileField field : fields) {
                fieldsByName.put(field.getField().getFieldName(), field);
            }
        }
        ClassFileField field = fieldsByName.get(name);
        if (field == null) throw new NoSuchFieldException(name);
        return field;
    }

    public List<ClassFileField> getFields() {
        return fields;
    }

    public List<Method> getMethods() {
        return methods;
    }

    public void removePointlessMethod(Method method) {
        methodsByName.remove(method.getName());
        methods.remove(method);
    }

    private List<Method> getMethodsWithMatchingName(final MethodPrototype prototype) {
        List<Method> named = Functional.filter(methods, new Predicate<Method>() {
            @Override
            public boolean test(Method in) {
                return in.getName().equals(prototype.getName());
            }
        });
        return named;
    }

    public OverloadMethodSet getOverloadMethodSet(final MethodPrototype prototype) {
        List<Method> named = getMethodsWithMatchingName(prototype);
        /*
         * Filter this list to find all methods with the name number of args.
         */
        final boolean isInstance = prototype.isInstanceMethod();
        final int numArgs = prototype.getArgs().size();
        final boolean isVarArgs = (prototype.isVarArgs());
        named = Functional.filter(named, new Predicate<Method>() {
            @Override
            public boolean test(Method in) {
                MethodPrototype other = in.getMethodPrototype();
                if (other.isInstanceMethod() != isInstance) return false;
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
        List<Method> named = getMethodsWithMatchingName(prototype);
        Method methodMatch = null;
        for (Method method : named) {
            MethodPrototype tgt = method.getMethodPrototype();
            if (tgt.equalsMatch(prototype)) return method;
            if (tgt.equalsGeneric(prototype)) {
                methodMatch = method;
            }
        }
        if (methodMatch != null) return methodMatch;
        throw new NoSuchMethodException();
    }

    public Method getMethodByPrototype(final MethodPrototype prototype, GenericTypeBinder binder) throws NoSuchMethodException {
        List<Method> named = getMethodsWithMatchingName(prototype);
        Method methodMatch = null;
        for (Method method : named) {
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

    public <X extends Attribute> X getAttributeByName(String name) {
        Attribute attribute = attributes.get(name);
        if (attribute == null) return null;
        @SuppressWarnings("unchecked")
        X tmp = (X) attribute;
        return tmp;
    }

    public AttributeBootstrapMethods getBootstrapMethods() {
        return getAttributeByName(AttributeBootstrapMethods.ATTRIBUTE_NAME);
    }

    public ConstantPoolEntryClass getThisClassConstpoolEntry() {
        return thisClass;
    }

    //    FIXME - inside constructor for inner class classfile
    private void markInnerClassAsStatic(Options options, ClassFile innerClass, JavaTypeInstance thisType) {
        /*
        * We need to tell the inner class it's a static, if it doesn't have the outer
        * class as a first constructor parameter, which is assigned to a synthetic local.
        *
        * TODO : Check assignment to synthetic local.
        *
        * (Either all will have it or none will).
        */
        List<Method> constructors = innerClass.getConstructors();
        InnerClassInfo innerClassInfo = innerClass.getClassType().getInnerClassHereInfo();
        if (!innerClassInfo.isInnerClass()) return;
        for (Method constructor : constructors) {
            List<JavaTypeInstance> params = constructor.getMethodPrototype().getArgs();
            if (params == null ||
                    params.isEmpty() ||
                    !params.get(0).equals(thisType)) {
                innerClass.markAsStatic();
                return;
            }
        }
        /*
         * Else it's not static.  If the params say so, tweak the inner class info to let
         * users know the first parameter is to be elided.
         */
        if (options.getOption(OptionsImpl.REMOVE_INNER_CLASS_SYNTHETICS)) {
            innerClassInfo.setHideSyntheticThis();
        }

    }

    // just after construction
    public void loadInnerClasses(DCCommonState dcCommonState) {
        Options options = dcCommonState.getOptions();

        AttributeInnerClasses attributeInnerClasses = getAttributeByName(AttributeInnerClasses.ATTRIBUTE_NAME);
        if (attributeInnerClasses == null) {
            return;
        }
        List<InnerClassAttributeInfo> innerClassAttributeInfoList = attributeInnerClasses.getInnerClassAttributeInfoList();

        JavaTypeInstance thisType = thisClass.getTypeInstance();


        for (InnerClassAttributeInfo innerClassAttributeInfo : innerClassAttributeInfoList) {
            JavaTypeInstance innerType = innerClassAttributeInfo.getInnerClassInfo();
            if (innerType == null) continue;

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
                markInnerClassAsStatic(options, innerClass, thisType);

                innerClassesByTypeInfo.put(innerType, new Pair<InnerClassAttributeInfo, ClassFile>(innerClassAttributeInfo, innerClass));
            } catch (CannotLoadClassException e) {
            }
        }
    }

    private void analyseInnerClassesPass1(DCCommonState state) {
        if (innerClassesByTypeInfo == null) return;
        for (Pair<InnerClassAttributeInfo, ClassFile> innerClassInfoClassFilePair : innerClassesByTypeInfo.values()) {
            ClassFile classFile = innerClassInfoClassFilePair.getSecond();
            classFile.analyseMid(state);
        }
    }

    private void analysePassOuterFirst(DCCommonState state) {
        try {
            CodeAnalyserWholeClass.wholeClassAnalysisPass2(this, state);
        } catch (RuntimeException e) {
            addComment("Exception performing whole class analysis ignored.", e);
        }

        if (innerClassesByTypeInfo == null) return;
        for (Pair<InnerClassAttributeInfo, ClassFile> innerClassInfoClassFilePair : innerClassesByTypeInfo.values()) {
            ClassFile classFile = innerClassInfoClassFilePair.getSecond();
            classFile.analysePassOuterFirst(state);
        }
    }

    public void analyseTop(DCCommonState dcCommonState) {
        analyseMid(dcCommonState);
        analysePassOuterFirst(dcCommonState);
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
                } catch (CannotLoadClassException e) {
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
                MethodPrototype prototype = method.getMethodPrototype();
                Method baseMethod = null;
                for (Triplet<JavaRefTypeInstance, ClassFile, GenericTypeBinder> bindTester : bindTesters) {
                    JavaRefTypeInstance refType = bindTester.getFirst();
                    ClassFile classFile = bindTester.getSecond();
                    GenericTypeBinder genericTypeBinder = bindTester.getThird();
                    try {
                        baseMethod = classFile.getMethodByPrototype(prototype, genericTypeBinder);
                    } catch (NoSuchMethodException e) {
                    }
                    if (baseMethod != null) break;
                }
                if (baseMethod != null) method.markOverride();
            }
        } catch (RuntimeException e) {
            addComment("Failed to analyse overrides", e);
        }
    }


    public void analyseMid(DCCommonState state) {
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

        for (Method method : methods) {
            method.analyse();
        }

        try {
            if (options.getOption(OptionsImpl.OVERRIDES, classFileVersion)) {
                analyseOverrides();
            }

            CodeAnalyserWholeClass.wholeClassAnalysisPass1(this, state);
        } catch (RuntimeException e) {
            addComment("Exception performing whole class analysis.");
        }

    }

    public JavaTypeInstance getClassType() {
        return thisClass.getTypeInstance();
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

    private ClassSignature getSignature(ConstantPool cp,
                                        ConstantPoolEntryClass rawSuperClass,
                                        List<ConstantPoolEntryClass> rawInterfaces) {
        AttributeSignature signatureAttribute = getAttributeByName(AttributeSignature.ATTRIBUTE_NAME);
        // If the class isn't generic (or has had the attribute removed), we have to use the
        // runtime type info.
        if (signatureAttribute == null) {
            List<JavaTypeInstance> interfaces = ListFactory.newList();
            for (ConstantPoolEntryClass rawInterface : rawInterfaces) {
                interfaces.add(rawInterface.getTypeInstance());
            }

            return new ClassSignature(null,
                    rawSuperClass == null ? null : rawSuperClass.getTypeInstance(),
                    interfaces);

        }
        return ConstantPoolUtils.parseClassSignature(signatureAttribute.getSignature(), cp);
    }


    public void dumpNamedInnerClasses(Dumper d) {
        if (innerClassesByTypeInfo == null || innerClassesByTypeInfo.isEmpty()) return;

        d.newln();

        for (Pair<InnerClassAttributeInfo, ClassFile> innerClassEntry : innerClassesByTypeInfo.values()) {
            // catchy!
            InnerClassInfo innerClassInfo = innerClassEntry.getFirst().getInnerClassInfo().getInnerClassHereInfo();
            if (innerClassInfo.isMethodScopedClass()) {
                continue;
            }
            ClassFile classFile = innerClassEntry.getSecond();
            if (classFile.hiddenInnerClass) {
                continue;
            }
            classFile.dumpHelper.dump(classFile, true, d);
            d.newln();
        }
    }

    @Override
    public Dumper dump(Dumper d) {
        return dumpHelper.dump(this, false, d);
    }

    public Dumper dumpAsInnerClass(Dumper d) {
        return dumpHelper.dump(this, true, d);
    }

    public String getFilePath() {
        return thisClass.getFilePath();
    }

    @Override
    public String toString() {
        return thisClass.getTextPath();
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

        getBoundSuperClasses2(classSignature.getSuperClass(), genericTypeBinder, boundSuperCollector, BindingSuperContainer.Route.EXTENSION);
        for (JavaTypeInstance interfaceBase : classSignature.getInterfaces()) {
            getBoundSuperClasses2(interfaceBase, genericTypeBinder, boundSuperCollector, BindingSuperContainer.Route.INTERFACE);
        }

        return boundSuperCollector.getBoundSupers();

    }

    public void getBoundSuperClasses(JavaTypeInstance boundGeneric, BoundSuperCollector boundSuperCollector, BindingSuperContainer.Route route) {
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
                genericTypeBinder = GenericTypeBinder.extractBindings(genericThisType, (JavaGenericRefTypeInstance) boundGeneric);
            } else {
                genericTypeBinder = null;
            }
        }
        /*
         * Now, apply this to each of our superclass/interfaces.
         */
        getBoundSuperClasses2(classSignature.getSuperClass(), genericTypeBinder, boundSuperCollector, route);
        for (JavaTypeInstance interfaceBase : classSignature.getInterfaces()) {
            getBoundSuperClasses2(interfaceBase, genericTypeBinder, boundSuperCollector, BindingSuperContainer.Route.INTERFACE);
        }
    }

    public GenericTypeBinder getGenericTypeBinder(JavaGenericRefTypeInstance boundGeneric) {
        JavaTypeInstance thisType = getClassSignature().getThisGeneralTypeClass(getClassType(), getConstantPool());
        if (!(thisType instanceof JavaGenericRefTypeInstance)) {
            return null;
        } else {
            JavaGenericRefTypeInstance genericThisType = (JavaGenericRefTypeInstance) thisType;
            return GenericTypeBinder.extractBindings(genericThisType, (JavaGenericRefTypeInstance) boundGeneric);
        }
    }

    private void getBoundSuperClasses2(JavaTypeInstance base, GenericTypeBinder genericTypeBinder, BoundSuperCollector boundSuperCollector, BindingSuperContainer.Route route) {
        if (base instanceof JavaRefTypeInstance) {
            // No bindings to do, can't go any further, mark relationship and move on.
            boundSuperCollector.collect((JavaRefTypeInstance) base, route);
            ClassFile classFile = ((JavaRefTypeInstance) base).getClassFile();
            if (classFile != null) classFile.getBoundSuperClasses(base, boundSuperCollector, route);
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
        } catch (CannotLoadClassException e) {
        }
        if (classFile == null) {
            return;
        }
        classFile.getBoundSuperClasses(boundBase, boundSuperCollector, route);
    }

    private List<ConstructorInvokationAnoynmousInner> anonymousUsages = ListFactory.newList();

    public void noteAnonymousUse(ConstructorInvokationAnoynmousInner anoynmousInner) {
        anonymousUsages.add(anoynmousInner);
    }

    public List<ConstructorInvokationAnoynmousInner> getAnonymousUsages() {
        return anonymousUsages;
    }
}
