package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 08:01
 */
public class JavaWildcardTypeInstance implements JavaGenericBaseInstance {
    private final WildcardType wildcardType;
    private final JavaTypeInstance underlyingType;

    public JavaWildcardTypeInstance(WildcardType wildcardType, JavaTypeInstance underlyingType) {
        this.wildcardType = wildcardType;
        this.underlyingType = underlyingType;
    }

    @Override
    public JavaTypeInstance getBoundInstance(GenericTypeBinder genericTypeBinder) {
        // TODO : Loses wildcard, do we care?
        if (underlyingType instanceof JavaGenericBaseInstance) {
            return ((JavaGenericBaseInstance) underlyingType).getBoundInstance(genericTypeBinder);
        } else {
            return underlyingType;
        }
    }

    @Override
    public boolean tryFindBinding(JavaTypeInstance other, GenericTypeBinder target) {
        if (underlyingType instanceof JavaGenericBaseInstance) {
            return ((JavaGenericBaseInstance) underlyingType).tryFindBinding(other, target);
        }
        return false;
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("? ").append(wildcardType).append(' ');
        sb.append(underlyingType.toString());
        return sb.toString();
    }

    @Override
    public String getRawName() {
        return toString();
    }

    @Override
    public JavaTypeInstance getArrayStrippedType() {
        return underlyingType.getArrayStrippedType();
    }

    @Override
    public int getNumArrayDimensions() {
        return underlyingType.getNumArrayDimensions();
    }

    @Override
    public int hashCode() {
        return (wildcardType.hashCode() * 31) + underlyingType.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JavaWildcardTypeInstance)) return false;
        JavaWildcardTypeInstance other = (JavaWildcardTypeInstance) o;
        return (other.wildcardType == wildcardType && other.underlyingType.equals(underlyingType));
    }

    @Override
    public boolean isComplexType() {
        return true;
    }

    @Override
    public boolean isUsableType() {
        return true;
    }

    // should be cached..
    @Override
    public JavaTypeInstance removeAnArrayIndirection() {
        // ??
        return underlyingType.removeAnArrayIndirection();
    }

    @Override
    public JavaTypeInstance getDeGenerifiedType() {
        return this;
    }

    @Override
    public RawJavaType getRawTypeOfSimpleType() {
        return underlyingType.getRawTypeOfSimpleType();
    }
}
