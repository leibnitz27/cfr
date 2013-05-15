package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.CodeAnalyserWholeClass;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.EnumClassRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.entities.attributes.Attribute;
import org.benf.cfr.reader.entities.attributes.AttributeBootstrapMethods;
import org.benf.cfr.reader.entities.attributes.AttributeInnerClasses;
import org.benf.cfr.reader.entities.attributes.AttributeSignature;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumper;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumperAnnotation;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumperInterface;
import org.benf.cfr.reader.entities.classfilehelpers.ClassFileDumperNormal;
import org.benf.cfr.reader.entities.innerclass.InnerClassAttributeInfo;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.configuration.ConfigCallback;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.CFRState;
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
public class ClassFile implements Dumpable {
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
    private Map<String, Method> methodsByName; // Lazily populated if interrogated.

    private final Map<JavaTypeInstance, Pair<InnerClassAttributeInfo, ClassFile>> innerClassesByTypeInfo; // populated if analysed.


    private final Map<String, Attribute> attributes;
    private final ConstantPoolEntryClass thisClass;
    private final ConstantPoolEntryClass rawSuperClass;
    private final List<ConstantPoolEntryClass> rawInterfaces;
    private final ClassSignature classSignature;

    private boolean begunAnalysis;

    /*
     * If this class represents a generated structure (like a switch lookup table)
     * we will mark it as hidden, and not normally show it.
     *
     * TODO:  TMI.
     */
    private boolean hiddenInnerClass;

    private ClassFileDumper dumpHelper;

    /*
     * Be sure to call loadInnerClasses directly after.
     */
    public ClassFile(final ByteData data, CFRState cfrState, boolean withInnerClasses, ConfigCallback configCallback) {
        int magic = data.getS4At(OFFSET_OF_MAGIC);
        if (magic != 0xCAFEBABE) throw new ConfusedCFRException("Magic != Cafebabe");

        minorVer = data.getS2At(OFFSET_OF_MINOR);
        majorVer = data.getS2At(OFFSET_OF_MAJOR);
        short constantPoolCount = data.getS2At(OFFSET_OF_CONSTANT_POOL_COUNT);
        this.constantPool = new ConstantPool(cfrState, data.getOffsetData(OFFSET_OF_CONSTANT_POOL), constantPoolCount);
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

        if (configCallback != null) {
            configCallback.configureWith(this);
        }
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
                        return new Method(arg, ClassFile.this, constantPool);
                    }
                });
        this.methods = tmpMethods;

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

//        constantPool.markClassNameUsed(constantPool.getUTF8Entry(thisClass.getNameIndex()).getValue());
        short superClassIndex = data.getS2At(OFFSET_OF_SUPER_CLASS);
        if (superClassIndex == 0) {
            rawSuperClass = null;
        } else {
            rawSuperClass = superClassIndex == 0 ? null : (ConstantPoolEntryClass) constantPool.getEntry(superClassIndex);
//            constantPool.markClassNameUsed(constantPool.getUTF8Entry(superClass.getNameIndex()).getValue());
        }
        this.classSignature = getSignature(constantPool, rawSuperClass, rawInterfaces);

        // Need to load inner classes asap so we can infer staticness before any analysis
        this.innerClassesByTypeInfo = new LinkedHashMap<JavaTypeInstance, Pair<InnerClassAttributeInfo, ClassFile>>();
        if (withInnerClasses) {
            loadInnerClasses(cfrState);
        }

        boolean isInterface = accessFlags.contains(AccessFlag.ACC_INTERFACE);
        boolean isAnnotation = accessFlags.contains(AccessFlag.ACC_ANNOTATION);
        /*
         * Choose a default dump helper.  This may be overwritten.
         */
        if (isInterface) {
            if (isAnnotation) {
                dumpHelper = new ClassFileDumperAnnotation();
            } else {
                dumpHelper = new ClassFileDumperInterface();
            }
        } else {
            dumpHelper = new ClassFileDumperNormal();
        }
    }

    public void setDumpHelper(ClassFileDumper dumpHelper) {
        this.dumpHelper = dumpHelper;
    }

    public void markHiddenInnerClass() {
        hiddenInnerClass = true;
    }

    public ClassFileVersion getClassFileVersion() {
        return new ClassFileVersion(majorVer, minorVer);
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
                fieldsByName.put(field.getField().getFieldName(constantPool), field);
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


    // We need to make sure we get the 'correct' method...
    public Method getMethodByPrototype(final MethodPrototype prototype) throws NoSuchMethodException {
        List<Method> named = Functional.filter(methods, new Predicate<Method>() {
            @Override
            public boolean test(Method in) {
                return in.getName().equals(prototype.getName());
            }
        });
        for (Method method : named) {
            MethodPrototype tgt = method.getMethodPrototype();
            if (tgt.equalsGeneric(prototype)) {
                return method;
            }
        }
        throw new NoSuchMethodException();
    }

    // Can't handle duplicates.  Remove?
    public Method getMethodByName(String name) throws NoSuchMethodException {
        if (methodsByName == null) {
            methodsByName = MapFactory.newMap();
            for (Method method : methods) {
                methodsByName.put(method.getName(), method);
            }
        }
        Method method = methodsByName.get(name);
        if (method == null) throw new NoSuchMethodException(name);
        return method;
    }

    public List<Method> getConstructors() {
        List<Method> res = ListFactory.newList();
        for (Method method : methods) {
            if (method.isConstructor()) res.add(method);
        }
        return res;
    }

    private <X extends Attribute> X getAttributeByName(String name) {
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
    private void markInnerClassAsStatic(CFRState cfrState, ClassFile innerClass, JavaTypeInstance thisType) {
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
        if (cfrState.removeInnerClassSynthetics()) {
            innerClassInfo.setHideSyntheticThis();
        }

    }

    // just after construction
    private void loadInnerClasses(CFRState cfrState) {
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
            ClassFile innerClass = cfrState.getClassFile(innerType, true);
            markInnerClassAsStatic(cfrState, innerClass, thisType);

            innerClassesByTypeInfo.put(innerType, new Pair<InnerClassAttributeInfo, ClassFile>(innerClassAttributeInfo, innerClass));
        }
    }

    private void analyseInnerClasses(CFRState state) {
        if (innerClassesByTypeInfo == null) return;
        for (Pair<InnerClassAttributeInfo, ClassFile> innerClassInfoClassFilePair : innerClassesByTypeInfo.values()) {
            ClassFile classFile = innerClassInfoClassFilePair.getSecond();
            classFile.analyseTop(state);
        }
    }

    public void analyseTop(CFRState state) {
        if (this.begunAnalysis) {
            return;
        }
        this.begunAnalysis = true;
        /*
         * Analyse inner classes first, so we know if they're static when we reference them
         * from the outer class.
         */
        if (state.analyseInnerClasses()) {
            analyseInnerClasses(state);
        }
        boolean exceptionRecovered = false;
        for (Method method : methods) {
            if (state.analyseMethod(method.getName())) {
                try {
                    method.analyse();
                } catch (Exception e) {
                    System.out.println("Exception analysing " + method.getName());
                    System.out.println(e);
                    for (StackTraceElement s : e.getStackTrace()) {
                        System.out.println(s);
                    }
                    exceptionRecovered = true;
                }
            }
        }
        if (exceptionRecovered) throw new ConfusedCFRException("Failed to analyse file");

        CodeAnalyserWholeClass.wholeClassAnalysis(this, state);
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

    private static final AccessFlag[] dumpableAccessFlagsInterface = new AccessFlag[]{
            AccessFlag.ACC_PUBLIC, AccessFlag.ACC_PRIVATE, AccessFlag.ACC_PROTECTED, AccessFlag.ACC_STATIC, AccessFlag.ACC_FINAL
    };

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
        if (innerClassesByTypeInfo == null) return;

        for (Pair<InnerClassAttributeInfo, ClassFile> innerClassEntry : innerClassesByTypeInfo.values()) {
            // catchy!
            InnerClassInfo innerClassInfo = innerClassEntry.getFirst().getInnerClassInfo().getInnerClassHereInfo();
            if (innerClassInfo.isAnoynmousInnerClass()) {
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

    public Dumper dump(Dumper d) {
        return dumpHelper.dump(this, false, d);
    }

    public String getFilePath() {
        return thisClass.getFilePath();
    }

    @Override
    public String toString() {
        return thisClass.getTextPath();
    }
}
