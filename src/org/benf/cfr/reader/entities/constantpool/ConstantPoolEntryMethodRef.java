package org.benf.cfr.reader.entities.constantpool;

import org.benf.cfr.reader.bytecode.analysis.variables.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableNamerDefault;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.AbstractConstantPoolEntry;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

public class ConstantPoolEntryMethodRef extends AbstractConstantPoolEntry {
    private static final long OFFSET_OF_CLASS_INDEX = 1;
    private static final long OFFSET_OF_NAME_AND_TYPE_INDEX = 3;
    private final boolean interfaceMethod;
    private static final VariableNamer fakeNamer = new VariableNamerDefault();
    private MethodPrototype methodPrototype = null;
    private OverloadMethodSet overloadMethodSet = null;

    private final short classIndex;
    private final short nameAndTypeIndex;

    public ConstantPoolEntryMethodRef(ConstantPool cp, ByteData data, boolean interfaceMethod) {
        super(cp);
        this.classIndex = data.getS2At(OFFSET_OF_CLASS_INDEX);
        this.nameAndTypeIndex = data.getS2At(OFFSET_OF_NAME_AND_TYPE_INDEX);
        this.interfaceMethod = interfaceMethod;
    }

    @Override
    public long getRawByteLength() {
        return 5;
    }

    @Override
    public void dump(Dumper d) {
        ConstantPool cp = getCp();
        d.print("Method " +
                cp.getNameAndTypeEntry(nameAndTypeIndex).getName().getValue() + ":" +
                cp.getNameAndTypeEntry(nameAndTypeIndex).getDescriptor().getValue());
    }

    public ConstantPool getCp() {
        return super.getCp();
    }

    @Override
    public String toString() {
        return "Method classIndex " + classIndex + " nameAndTypeIndex " + nameAndTypeIndex;
    }

    public ConstantPoolEntryClass getClassEntry() {
        return getCp().getClassEntry(classIndex);
    }

    public ConstantPoolEntryNameAndType getNameAndTypeEntry() {
        return getCp().getNameAndTypeEntry(nameAndTypeIndex);
    }

    //
    // This is inferior to the method based version, as we don't have generic signatures.
    //
    public MethodPrototype getMethodPrototype() {
        if (methodPrototype == null) {
            ConstantPool cp = getCp();
            JavaTypeInstance classType = cp.getClassEntry(classIndex).getTypeInstance();
            // Figure out the non generic version of this
            ConstantPoolEntryNameAndType nameAndType = cp.getNameAndTypeEntry(nameAndTypeIndex);
            ConstantPoolEntryUTF8 descriptor = nameAndType.getDescriptor();
            MethodPrototype basePrototype = ConstantPoolUtils.parseJavaMethodPrototype(null, classType, getName(), interfaceMethod, Method.MethodConstructor.NOT, descriptor, cp, false /* we can't tell */, fakeNamer);
            // See if we can load the class to get a signature version of this prototype.
            // TODO : Improve the caching?

            try {
                JavaTypeInstance loadType = classType.getArrayStrippedType().getDeGenerifiedType();
                ClassFile classFile = cp.getDCCommonState().getClassFile(loadType);
                MethodPrototype replacement = classFile.getMethodByPrototype(basePrototype).getMethodPrototype();

                overloadMethodSet = classFile.getOverloadMethodSet(replacement);
                basePrototype = replacement;
            } catch (NoSuchMethodException ignore) {
                int x = 1;
            } catch (CannotLoadClassException ignore) {
                int x = 1;
            }

            methodPrototype = basePrototype;
        }
        return methodPrototype;
    }


    public OverloadMethodSet getOverloadMethodSet() {
        return overloadMethodSet;
    }

    public String getName() {
        return getCp().getNameAndTypeEntry(nameAndTypeIndex).getName().getValue();
    }

    public boolean isInitMethod() {
        String name = getName();
        return MiscConstants.INIT_METHOD.equals(name);
    }
}
