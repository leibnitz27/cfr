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
    private final JavaRefTypeInstance outerClass;

    private JavaRefTypeInstance(String className, ConstantPool cp) {
        this.className = className;
        this.cp = cp;
        JavaRefTypeInstance outerClassTmp = null;
        if (className.contains("$")) {
            String outer = className.substring(0, className.lastIndexOf('$'));
            outerClassTmp = cp.getRefClassFor(outer);
        }
        this.outerClass = outerClassTmp;
    }

    /*
     * Only call from constPool cache.
     */
    public static JavaRefTypeInstance create(String rawClassName, ConstantPool cp) {
        return new JavaRefTypeInstance(rawClassName, cp);
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
    public boolean isInnerClassOf(JavaTypeInstance possibleParent) {
        if (outerClass == null) return false;
        return outerClass.equals(possibleParent);
    }

    @Override
    public boolean isInnerClass() {
        return (outerClass != null);
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
