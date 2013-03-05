package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.ConstantPool;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 08:01
 */
public class JavaGenericPlaceholderTypeInstance implements JavaGenericBaseInstance {
    private final String className;
    private final ConstantPool cp;

    public JavaGenericPlaceholderTypeInstance(String className, ConstantPool cp) {
        this.className = className;
        this.cp = cp;
    }

    @Override
    public JavaTypeInstance getBoundInstance(GenericTypeBinder genericTypeBinder) {
        return genericTypeBinder.getBindingFor(this);
    }

    /*
     * TODO : Strictly speaking we should only be adding the binding here if className is in formal parameters.
     */
    @Override
    public boolean tryFindBinding(JavaTypeInstance other, GenericTypeBinder target) {
        target.suggestBindingFor(className, other);
        return true;
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }

    @Override
    public String toString() {
        return className;
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
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof JavaGenericPlaceholderTypeInstance)) return false;
        JavaGenericPlaceholderTypeInstance other = (JavaGenericPlaceholderTypeInstance) o;
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
        throw new UnsupportedOperationException();
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
