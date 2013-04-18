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
public class ConstantPoolEntryMethodRef extends AbstractConstantPoolEntry {
    private final long OFFSET_OF_CLASS_INDEX = 1;
    private final long OFFSET_OF_NAME_AND_TYPE_INDEX = 3;
    private final boolean interfaceMethod;
    private static final VariableNamer fakeNamer = new VariableNamerDefault();
    private MethodPrototype methodPrototype = null;

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
    public void dump(Dumper d, ConstantPool cp) {
        d.print("Method " +
                cp.getNameAndTypeEntry(nameAndTypeIndex).getName(cp).getValue() + ":" +
                cp.getNameAndTypeEntry(nameAndTypeIndex).getDescriptor(cp).getValue());
    }

    @Override
    public String toString() {
        return "Method classIndex " + classIndex + " nameAndTypeIndex " + nameAndTypeIndex;
    }

    public ConstantPoolEntryClass getClassEntry() {
        return getCp().getClassEntry(classIndex);
    }

    public short getNameAndTypeIndex() {
        return nameAndTypeIndex;
    }

    //
    // This is inferior to the method based version, as we don't have generic signatures.
    //
    public MethodPrototype getMethodPrototype() {
        if (methodPrototype == null) {
            ConstantPool cp = getCp();
            JavaTypeInstance classType = cp.getClassEntry(classIndex).getTypeInstance(cp);
            // Figure out the non generic version of this
            ConstantPoolEntryNameAndType nameAndType = cp.getNameAndTypeEntry(nameAndTypeIndex);
            ConstantPoolEntryUTF8 descriptor = nameAndType.getDescriptor(cp);
            MethodPrototype basePrototype = ConstantPoolUtils.parseJavaMethodPrototype(null, getName(), interfaceMethod, cp.getNameAndTypeEntry(nameAndTypeIndex).getDescriptor(cp), cp, false /* we can't tell */, fakeNamer);
            // See if we can load the class to get a signature version of this prototype.
            // TODO : Improve the caching?

            try {
                ClassFile classFile = cp.getCFRState().getClassFile(classType.getDeGenerifiedType(), false);
                MethodPrototype replacement = classFile.getMethodByPrototype(basePrototype).getMethodPrototype();
                basePrototype = replacement;
            } catch (NoSuchMethodException ignore) {
            } catch (CannotLoadClassException ignore) {
            }

            methodPrototype = basePrototype;
        }
        return methodPrototype;
    }

    public String getName() {
        return getCp().getNameAndTypeEntry(nameAndTypeIndex).getName(getCp()).getValue();
    }

    public boolean isInitMethod() {
        String name = getName();
        return "<init>".equals(name);
    }
}
