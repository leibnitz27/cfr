package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.attributes.Attribute;
import org.benf.cfr.reader.entityfactories.AttributeFactory;
import org.benf.cfr.reader.entityfactories.ContiguousEntityFactory;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 18:25
 * To change this template use File | Settings | File Templates.
 */
public class ClassFile {
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
    private final List<Field> fields;
    private final List<Method> methods;
    private final List<Attribute> attributes;
    private final ConstantPoolEntryClass thisClass;
    private final ConstantPoolEntryClass superClass;
    private final List<ConstantPoolEntryClass> interfaces;

    public ClassFile(final ByteData data) {
        int magic = data.getS4At(OFFSET_OF_MAGIC);
        if (magic != 0xCAFEBABE) throw new ConfusedCFRException("Magic != Cafebabe");

        minorVer = data.getS2At(OFFSET_OF_MINOR);
        majorVer = data.getS2At(OFFSET_OF_MAJOR);
        short constantPoolCount = data.getS2At(OFFSET_OF_CONSTANT_POOL_COUNT);
        this.constantPool = new ConstantPool(data.getOffsetData(OFFSET_OF_CONSTANT_POOL), constantPoolCount);
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

        this.interfaces = tmpInterfaces;


        accessFlags = AccessFlag.build(data.getS2At(OFFSET_OF_ACCESS_FLAGS));

        final long OFFSET_OF_FIELDS_COUNT = OFFSET_OF_INTERFACES + 2 * numInterfaces;
        final long OFFSET_OF_FIELDS = OFFSET_OF_FIELDS_COUNT + 2;
        final short numFields = data.getS2At(OFFSET_OF_FIELDS_COUNT);
        ArrayList<Field> tmpFields = new ArrayList<Field>();
        tmpFields.ensureCapacity(numFields);
        final long fieldsLength = ContiguousEntityFactory.build(data.getOffsetData(OFFSET_OF_FIELDS), numFields, tmpFields,
                new UnaryFunction<ByteData, Field>() {
                    @Override
                    public Field invoke(ByteData arg) {
                        return new Field(arg, constantPool);
                    }
                });
        this.fields = tmpFields;

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
        this.attributes = tmpAttributes;

        thisClass = (ConstantPoolEntryClass) constantPool.getEntry(data.getS2At(OFFSET_OF_THIS_CLASS));
//        constantPool.markClassNameUsed(constantPool.getUTF8Entry(thisClass.getNameIndex()).getValue());
        short superClassIndex = data.getS2At(OFFSET_OF_SUPER_CLASS);
        if (superClassIndex == 0) {
            superClass = null;
        } else {
            superClass = superClassIndex == 0 ? null : (ConstantPoolEntryClass) constantPool.getEntry(superClassIndex);
//            constantPool.markClassNameUsed(constantPool.getUTF8Entry(superClass.getNameIndex()).getValue());
        }
    }

    public void analyse() {
        boolean exceptionRecovered = false;
        for (Method method : methods) {
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
        if (exceptionRecovered) throw new RuntimeException("Failed to analyse file");
    }

    public JavaTypeInstance getClassType() {
        return thisClass.getTypeInstance(constantPool);
    }

    public void Dump(Dumper d) {
        d.line();
        d.print("// Imports\n");
        constantPool.dumpImports(d);
        d.print("class " + thisClass.getTypeInstance(constantPool) + "\n");
        if (superClass != null) {
            d.print("extends " + superClass.getTypeInstance(constantPool) + "\n");
        }
        if (!interfaces.isEmpty()) {
            d.print("implements ");
            int size = interfaces.size();
            for (int x = 0; x < size; ++x) {
                ConstantPoolEntryClass iface = interfaces.get(x);
                d.print("" + iface.getTypeInstance(constantPool) + (x < (size - 1) ? ",\n" : "\n"));
            }
        }
        d.removePendingCarriageReturn();
        d.print("{\n");

        if (!fields.isEmpty()) {
            d.print("// Fields\n");
            for (Field field : fields) {
                field.dump(d, constantPool);
            }
        }
//        d.print("// Attributes\n");
//        for (Attribute attr : attributes) {
//            d.newln();
//            attr.dump(d, constantPool);
//        }
//        d.line();
        if (!methods.isEmpty()) {
            d.print("// Methods\n");
            for (Method meth : methods) {
                d.newln();
                meth.dump(d, constantPool);
            }
        }
        d.newln();
        d.print("}\n");
    }

    public void dumpMethod(String name, Dumper dumper) {
        for (Method method : methods) {
            if (method.getName().equals(name)) {
                dumper.newln();
                method.dump(dumper, constantPool);
            }
        }
    }
}
