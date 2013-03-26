package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.ConstantPool;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 08:01
 */
public class JavaRefTypeInstance implements JavaTypeInstance {
    private final String className;
    private final ConstantPool cp;

    public JavaRefTypeInstance(String rawClassName, ConstantPool cp) {
        this.className = ClassNameUtils.convertFromPath(rawClassName);
        this.cp = cp;
        cp.markClassNameUsed(this.className);
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }

    @Override
    public String toString() {
        return cp.getDisplayableClassName(className);
    }

    @Override
    public JavaTypeInstance getArrayStrippedType() {
        return this;
    }

    @Override
    public int getNumArrayDimensions() {
        return 0;
    }

    @Override
    public String getRawName() {
        return className;
    }

    @Override
    public int hashCode() {
        return 31 + className.hashCode();
    }

    @Override
    public boolean isDirectInnerClassType(JavaTypeInstance possibleChild) {
        if (!(possibleChild instanceof JavaRefTypeInstance)) return false;

        String thisClassname = className;
        String otherClassname = ((JavaRefTypeInstance) possibleChild).className;
        if (!otherClassname.startsWith(thisClassname)) return false;
        if (otherClassname.length() < thisClassname.length() + 1) return false;
        String postfix = otherClassname.substring(thisClassname.length() + 1);
        if (postfix.contains("$")) return false;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof JavaRefTypeInstance)) return false;
        JavaRefTypeInstance other = (JavaRefTypeInstance) o;
        return other.className.equals(className);
    }

    @Override
    public boolean isComplexType() {
        return true;
    }

    @Override
    public boolean isUsableType() {
        return true;
    }

    @Override
    public JavaTypeInstance removeAnArrayIndirection() {
//        if (GlobalArgs.lenient) {
//            return this;
//        } else {
        throw new UnsupportedOperationException("Trying to remove an array indirection on a ref type");
//        }
    }

    @Override
    public JavaTypeInstance getDeGenerifiedType() {
        return this;
    }

    @Override
    public RawJavaType getRawTypeOfSimpleType() {
        return RawJavaType.REF;
    }


}
