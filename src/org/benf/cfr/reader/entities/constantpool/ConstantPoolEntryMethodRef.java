package org.benf.cfr.reader.entities.constantpool;

import org.benf.cfr.reader.bytecode.analysis.types.BindingSuperContainer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
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
import java.util.Collection;

public class ConstantPoolEntryMethodRef extends AbstractConstantPoolEntry {
    private static final long OFFSET_OF_CLASS_INDEX = 1;
    private static final long OFFSET_OF_NAME_AND_TYPE_INDEX = 3;
    private final boolean interfaceMethod;
    private static final VariableNamer fakeNamer = new VariableNamerDefault();
    private MethodPrototype methodPrototype = null;
    private OverloadMethodSet overloadMethodSet = null;

    private final int classIndex;
    private final int nameAndTypeIndex;

    public ConstantPoolEntryMethodRef(ConstantPool cp, ByteData data, boolean interfaceMethod) {
        super(cp);
        this.classIndex = data.getU2At(OFFSET_OF_CLASS_INDEX);
        this.nameAndTypeIndex = data.getU2At(OFFSET_OF_NAME_AND_TYPE_INDEX);
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
            MethodPrototype basePrototype = ConstantPoolUtils.parseJavaMethodPrototype(null, classType, getName(), /* interfaceMethod */ false, Method.MethodConstructor.NOT, descriptor, cp, false /* we can't tell */, false, fakeNamer);
            // See if we can load the class to get a signature version of this prototype.
            // TODO : Improve the caching?

            findBetterMethod : try {
                JavaTypeInstance loadType = classType.getArrayStrippedType().getDeGenerifiedType();
                ClassFile classFile = cp.getDCCommonState().getClassFile(loadType);
                MethodPrototype replacement;
                foundBetterMethod : try {
                    replacement = classFile.getMethodByPrototype(basePrototype).getMethodPrototype();
                } catch (NoSuchMethodException e) {
                    if (basePrototype.getName().equals(MiscConstants.INIT_METHOD)) break findBetterMethod;
                    // The method is not present here.
                    // This means we might have inherited an implementation from a base class
                    // (or, in java8, from a base implementation, but not handling that yet!)
                    BindingSuperContainer bindingSuperContainer = classFile.getBindingSupers();
                    if (bindingSuperContainer == null) break findBetterMethod;
                    Collection<JavaRefTypeInstance> supers = bindingSuperContainer.getBoundSuperClasses().keySet();
                    for (JavaTypeInstance supertype : supers) {
                        loadType = supertype.getDeGenerifiedType();
                        ClassFile superClassFile = cp.getDCCommonState().getClassFile(loadType);
                        try {
                            MethodPrototype baseReplacement = superClassFile.getMethodByPrototype(basePrototype).getMethodPrototype();
                            /*
                             * Ok, one of our bases actually implements this.  Now we /PRETEND/ the class we're
                             * interrogating implemented it.  This means we need to rewrite any generics so that they
                             * are using the bindings present in the underlying class.
                             * (i.e. if we are trying to find add<T> , and we've found add<E>, because the
                             * child class has T->base E.)
                             */
                            classFile = superClassFile;
                            replacement = baseReplacement;
                            break foundBetterMethod;
                        } catch (NoSuchMethodException e2) {
                        }
                    }
                    break findBetterMethod;
                }

                overloadMethodSet = classFile.getOverloadMethodSet(replacement);
                basePrototype = replacement;
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
