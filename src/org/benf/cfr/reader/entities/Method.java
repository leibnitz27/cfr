package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamerFactory;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.entities.attributes.*;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.output.CommaHelp;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 17/04/2011
 * Time: 21:32
 * To change this template use File | Settings | File Templates.
 */

/* Too much in common with field - refactor.
 *
 */

public class Method implements KnowsRawSize {

    public enum MethodConstructor {
        NOT(false),
        STATIC_CONSTRUCTOR(false),
        CONSTRUCTOR(true),
        ENUM_CONSTRUCTOR(true);

        private final boolean isConstructor;

        private MethodConstructor(boolean isConstructor) {
            this.isConstructor = isConstructor;
        }

        public boolean isConstructor() {
            return isConstructor;
        }
    }

    private static final long OFFSET_OF_ACCESS_FLAGS = 0;
    private static final long OFFSET_OF_NAME_INDEX = 2;
    private static final long OFFSET_OF_DESCRIPTOR_INDEX = 4;
    private static final long OFFSET_OF_ATTRIBUTES_COUNT = 6;
    private static final long OFFSET_OF_ATTRIBUTES = 8;

    private final long length;
    private final Set<AccessFlagMethod> accessFlags;
    private final Map<String, Attribute> attributes;
    private final String name;
    private MethodConstructor isConstructor;
    private final short descriptorIndex;
    private final AttributeCode codeAttribute;
    private final ConstantPool cp;
    private final VariableNamer variableNamer;
    private final MethodPrototype methodPrototype;
    private final ClassFile classFile;
    private boolean hidden;

    public Method(ByteData raw, ClassFile classFile, final ConstantPool cp) {
        this.cp = cp;
        this.classFile = classFile;
        this.accessFlags = AccessFlagMethod.build(raw.getS2At(OFFSET_OF_ACCESS_FLAGS));
        this.descriptorIndex = raw.getS2At(OFFSET_OF_DESCRIPTOR_INDEX);
        short nameIndex = raw.getS2At(OFFSET_OF_NAME_INDEX);
        short numAttributes = raw.getS2At(OFFSET_OF_ATTRIBUTES_COUNT);
        ArrayList<Attribute> tmpAttributes = new ArrayList<Attribute>();
        tmpAttributes.ensureCapacity(numAttributes);
        long attributesLength = ContiguousEntityFactory.build(raw.getOffsetData(OFFSET_OF_ATTRIBUTES), numAttributes, tmpAttributes,
                new UnaryFunction<ByteData, Attribute>() {
                    @Override
                    public Attribute invoke(ByteData arg) {
                        return AttributeFactory.build(arg, cp);
                    }
                });
        this.attributes = ContiguousEntityFactory.addToMap(new HashMap<String, Attribute>(), tmpAttributes);
        this.length = OFFSET_OF_ATTRIBUTES + attributesLength;
        Attribute codeAttribute = attributes.get(AttributeCode.ATTRIBUTE_NAME);
        if (codeAttribute == null) {
            // Because we don't have a code attribute, we don't have a local variable table.
            this.variableNamer = VariableNamerFactory.getNamer(null, cp);
            this.codeAttribute = null;
        } else {
            this.codeAttribute = (AttributeCode) codeAttribute;
            // This rigamarole is neccessary because we don't provide the factory for the code attribute enough information
            // get get the Method (this).
            this.variableNamer = VariableNamerFactory.getNamer(this.codeAttribute.getLocalVariableTable(), cp);
            this.codeAttribute.setMethod(this);
        }

        this.name = cp.getUTF8Entry(nameIndex).getValue();
        MethodConstructor methodConstructor = MethodConstructor.NOT;
        if (name.equals(MiscConstants.INIT_METHOD)) {
            boolean isEnum = classFile.getAccessFlags().contains(AccessFlag.ACC_ENUM);
            methodConstructor = isEnum ? MethodConstructor.ENUM_CONSTRUCTOR : MethodConstructor.CONSTRUCTOR;
        } else if (name.equals(MiscConstants.STATIC_INIT_METHOD)) {
            methodConstructor = MethodConstructor.STATIC_CONSTRUCTOR;
        }
        this.isConstructor = methodConstructor;
        if (isConstructor() && accessFlags.contains(AccessFlagMethod.ACC_STRICT)) {
            accessFlags.remove(AccessFlagMethod.ACC_STRICT);
            classFile.getAccessFlags().add(AccessFlag.ACC_STRICT);
        }

        this.methodPrototype = generateMethodPrototype();
        if (accessFlags.contains(AccessFlagMethod.ACC_BRIDGE) &&
                cp.getCFRState().hideBridgeMethods()) {
            this.hidden = true;
        }
    }

    public Set<AccessFlagMethod> getAccessFlags() {
        return accessFlags;
    }

    public void hideSynthetic() {
        this.hidden = true;
    }

    public boolean isHiddenFromDisplay() {
        return hidden;
    }

    public boolean testAccessFlag(AccessFlagMethod flag) {
        return accessFlags.contains(flag);
    }

    public MethodConstructor getConstructorFlag() {
        return isConstructor;
    }

    private AttributeSignature getSignatureAttribute() {
        return this.<AttributeSignature>getAttributeByName(AttributeSignature.ATTRIBUTE_NAME);
    }

    private <T extends Attribute> T getAttributeByName(String name) {
        Attribute attribute = attributes.get(name);
        if (attribute == null) return null;
        @SuppressWarnings("unchecked")
        T tmp = (T) attribute;
        return tmp;
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
        return name;
    }

    /* This is a bit ugly - otherwise though we need to tie a variable namer to this earlier.
     * We can't always use the signature... in an enum, for example, it lies!
     *
     * Method  : <init> name : 30, descriptor 31
     * Descriptor ConstantUTF8[(Ljava/lang/String;I)V]
     * Signature Signature:ConstantUTF8[()V]
     *
     *
     */
    private MethodPrototype generateMethodPrototype() {
        AttributeSignature sig = getSignatureAttribute();
        ConstantPoolEntryUTF8 signature = sig == null ? null : sig.getSignature();
        ConstantPoolEntryUTF8 descriptor = cp.getUTF8Entry(descriptorIndex);
        ConstantPoolEntryUTF8 prototype = null;
        if (signature == null) {
            prototype = descriptor;
        } else {
            prototype = signature;
        }
        boolean isInstance = !accessFlags.contains(AccessFlagMethod.ACC_STATIC);
        boolean isVarargs = accessFlags.contains(AccessFlagMethod.ACC_VARARGS);
        MethodPrototype res = ConstantPoolUtils.parseJavaMethodPrototype(classFile, classFile.getClassType(), getName(), isInstance, prototype, cp, isVarargs, variableNamer);
        /*
         * Work around bug in inner class signatures.
         *
         * http://stackoverflow.com/questions/15131040/java-inner-class-inconsistency-between-descriptor-and-signature-attribute-clas
         */
        if (classFile.isInnerClass()) {
            if (signature != null) {
                MethodPrototype descriptorProto = ConstantPoolUtils.parseJavaMethodPrototype(classFile, classFile.getClassType(), getName(), isInstance, descriptor, cp, isVarargs, variableNamer);
                if (descriptorProto.getArgs().size() != res.getArgs().size()) {
                    // error due to inner class sig bug.
                    res = fixupInnerClassSignature(descriptorProto, res);
                }
            }
        }
        return res;
    }

    private static MethodPrototype fixupInnerClassSignature(MethodPrototype descriptor, MethodPrototype signature) {
        List<JavaTypeInstance> descriptorArgs = descriptor.getArgs();
        List<JavaTypeInstance> signatureArgs = signature.getArgs();
        if (signatureArgs.size() != descriptorArgs.size() - 1) {
            // It's not the known issue, can't really deal with it.
            return signature;
        }
        for (int x = 0; x < signatureArgs.size(); ++x) {
            if (!descriptorArgs.get(x + 1).equals(signatureArgs.get(x).getDeGenerifiedType())) {
                // Incompatible.
                return signature;
            }
        }
        // Ok.  We've fallen foul of the bad signature-on-inner-class
        // compiler bug.  Patch up the inner class signature so that it takes the implicit
        // outer this pointer.
        // Since we've got the ref to the mutable signatureArgs, let's be DISGUSTING and mutate that.
        signatureArgs.add(0, descriptorArgs.get(0));
        return signature;
    }

    public MethodPrototype getMethodPrototype() {
        return methodPrototype;
    }

    private void dumpMethodAnnotations(Dumper d) {
        AttributeRuntimeVisibleAnnotations runtimeVisibleAnnotations = getAttributeByName(AttributeRuntimeVisibleAnnotations.ATTRIBUTE_NAME);
        AttributeRuntimeInvisibleAnnotations runtimeInvisibleAnnotations = getAttributeByName(AttributeRuntimeInvisibleAnnotations.ATTRIBUTE_NAME);
        if (runtimeVisibleAnnotations != null) runtimeVisibleAnnotations.dump(d);
        if (runtimeInvisibleAnnotations != null) runtimeInvisibleAnnotations.dump(d);
    }

    public void dumpSignatureText(boolean asClass, Dumper d) {

        dumpMethodAnnotations(d);

        Set<AccessFlagMethod> localAccessFlags = accessFlags;
        if (!asClass) {
            if (codeAttribute != null) d.print("default ");
            // Dumping as interface.
            localAccessFlags = SetFactory.newSet(localAccessFlags);
            localAccessFlags.remove(AccessFlagMethod.ACC_ABSTRACT);
        }
        String prefix = CollectionUtils.join(localAccessFlags, " ");

        if (!prefix.isEmpty()) d.print(prefix);

        if (isConstructor == MethodConstructor.STATIC_CONSTRUCTOR) {
            return;
        }

        if (!prefix.isEmpty()) d.print(' ');

        MethodPrototypeAnnotationsHelper paramAnnotationsHelper = new MethodPrototypeAnnotationsHelper(
                this.<AttributeRuntimeVisibleParameterAnnotations>getAttributeByName(AttributeRuntimeVisibleParameterAnnotations.ATTRIBUTE_NAME),
                this.<AttributeRuntimeInvisibleParameterAnnotations>getAttributeByName(AttributeRuntimeInvisibleParameterAnnotations.ATTRIBUTE_NAME)
        );

        String displayName = name;
        if (isConstructor.isConstructor()) {
            displayName = classFile.getClassType().toString();
        }
        d.print(getMethodPrototype().getDeclarationSignature(displayName, isConstructor, paramAnnotationsHelper));
        AttributeExceptions exceptionsAttribute = getAttributeByName(AttributeExceptions.ATTRIBUTE_NAME);
        if (exceptionsAttribute != null) {
            d.print(" throws ");
            boolean first = true;
            List<ConstantPoolEntryClass> exceptionClasses = exceptionsAttribute.getExceptionClassList();
            for (ConstantPoolEntryClass exceptionClass : exceptionClasses) {
                first = CommaHelp.comma(first, d);
                JavaTypeInstance typeInstance = exceptionClass.getTypeInstance();
                d.print(typeInstance.toString());
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

    public void analyse() {
        try {
            if (codeAttribute != null) codeAttribute.analyse();
        } catch (RuntimeException e) {
            System.out.println("While processing method : " + this.getName());
            throw e;
        }
    }

    public boolean hasCodeAttribute() {
        return codeAttribute != null;
    }

    public void dump(Dumper d, boolean asClass) {
        dumpSignatureText(asClass, d);
        if (codeAttribute == null) {
            AttributeAnnotationDefault annotationDefault = getAttributeByName(AttributeAnnotationDefault.ATTRIBUTE_NAME);
            if (annotationDefault != null) {
                d.print(" default ").dump(annotationDefault.getElementValue());
            }
            d.print(";");
        } else {
            d.print(' ').dump(codeAttribute);
        }
    }


}
