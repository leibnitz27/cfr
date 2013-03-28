package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamerDefault;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 20:37
 * To change this template use File | Settings | File Templates.
 */
public class ConstantPoolEntryMethodRef implements ConstantPoolEntry {
    private final long OFFSET_OF_CLASS_INDEX = 1;
    private final long OFFSET_OF_NAME_AND_TYPE_INDEX = 3;
    private final boolean interfaceMethod;
    private static final VariableNamer fakeNamer = new VariableNamerDefault();
    private MethodPrototype methodPrototype = null;

    private final short classIndex;
    private final short nameAndTypeIndex;

    public ConstantPoolEntryMethodRef(ByteData data, boolean interfaceMethod) {
        this.classIndex = data.getS2At(OFFSET_OF_CLASS_INDEX);
        this.nameAndTypeIndex = data.getS2At(OFFSET_OF_NAME_AND_TYPE_INDEX);
        this.interfaceMethod = interfaceMethod;
    }

    @Override
    public long getRawByteLength() {
        return 5;
    }

    @Override
    public void dump(Dumper d, ConstantPool cp) {
        d.print("Method " +
                cp.getNameAndTypeEntry(nameAndTypeIndex).getName(cp).getValue() + ":" +
                cp.getNameAndTypeEntry(nameAndTypeIndex).getDescriptor(cp).getValue());
    }

    @Override
    public String toString() {
        return "Method classIndex " + classIndex + " nameAndTypeIndex " + nameAndTypeIndex;
    }

    public short getClassIndex() {
        return classIndex;
    }

    public short getNameAndTypeIndex() {
        return nameAndTypeIndex;
    }

    //
    // This is inferior to the method based version, as we don't have generic signatures.
    //
    public MethodPrototype getMethodPrototype(ConstantPool cp) {
        if (methodPrototype == null) {
            JavaTypeInstance classType = cp.getClassEntry(classIndex).getTypeInstance(cp);
            // Figure out the non generic version of this
            MethodPrototype basePrototype = ConstantPoolUtils.parseJavaMethodPrototype(null, getName(cp), interfaceMethod, cp.getNameAndTypeEntry(nameAndTypeIndex).getDescriptor(cp), cp, false /* we can't tell */, fakeNamer);
            // See if we can load the class to get a signature version of this prototype.
            // TODO : Improve the caching?

            try {
                ClassFile classFile = cp.getCFRState().getClassFile(classType.getDeGenerifiedType(), false);
                MethodPrototype replacement = classFile.getMethodByPrototype(basePrototype).getMethodPrototype();
                basePrototype = replacement;
            } catch (NoSuchMethodException _) {
            } catch (CannotLoadClassException _) {
            }

            methodPrototype = basePrototype;
        }
        return methodPrototype;
    }

    public String getName(ConstantPool cp) {
        return cp.getNameAndTypeEntry(nameAndTypeIndex).getName(cp).getValue();
    }

    public boolean isInitMethod(ConstantPool cp) {
        String name = getName(cp);
        return "<init>".equals(name);
    }
}
