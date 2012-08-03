package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 08:01
 */
public class JavaArrayTypeInstance implements JavaTypeInstance {
    private final int dimensions;
    private final JavaTypeInstance underlyingType;

    public JavaArrayTypeInstance(int dimensions, JavaTypeInstance underlyingType) {
        this.dimensions = dimensions;
        this.underlyingType = underlyingType;
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }

    @Override
    public String toString() {
        return getBeforeNewString() + getAfterNewString();
    }

    @Override
    public String getBeforeNewString() {
        return underlyingType.toString();
    }

    @Override
    public String getAfterNewString() {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < dimensions; ++x) {
            sb.append("[]");
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return (dimensions * 31) + underlyingType.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JavaArrayTypeInstance)) return false;
        JavaArrayTypeInstance other = (JavaArrayTypeInstance) o;
        return (other.dimensions == dimensions && other.underlyingType.equals(underlyingType));
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
        if (dimensions == 1) return underlyingType;
        return new JavaArrayTypeInstance(dimensions - 1, underlyingType);
    }

    @Override
    public RawJavaType getRawTypeOfSimpleType() {
        return underlyingType.getRawTypeOfSimpleType();
    }
}
