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
    private JavaTypeInstance cachedDegenerifiedType;

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
        return toCommonString(getNumArrayDimensions());
    }

    private String toCommonString(int numDims) {
        StringBuilder sb = new StringBuilder();
        sb.append(underlyingType.getArrayStrippedType().toString());
        for (int x = 0; x < numDims; ++x) {
            sb.append("[]");
        }
        return sb.toString();
    }

    public String toVarargString() {
        return toCommonString(getNumArrayDimensions() - 1) + " ...";
    }

    @Override
    public String getRawName() {
        return toString();
    }

    @Override
    public InnerClassInfo getInnerClassHereInfo() {
        return InnerClassInfo.NOT;
    }

    @Override
    public BindingSuperContainer getBindingSupers() {
        return null;
    }

    @Override
    public JavaTypeInstance getArrayStrippedType() {
        return underlyingType.getArrayStrippedType();
    }

    @Override
    public int getNumArrayDimensions() {
        return dimensions + underlyingType.getNumArrayDimensions();
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
    public JavaTypeInstance getDeGenerifiedType() {
        if (cachedDegenerifiedType == null) {
            cachedDegenerifiedType = new JavaArrayTypeInstance(dimensions, underlyingType.getDeGenerifiedType());
        }
        return cachedDegenerifiedType;
    }

    @Override
    public RawJavaType getRawTypeOfSimpleType() {
        return underlyingType.getRawTypeOfSimpleType();
    }

    @Override
    public boolean implicitlyCastsTo(JavaTypeInstance other) {
        if (other == TypeConstants.OBJECT) return true;
        if (other instanceof JavaArrayTypeInstance) {
            JavaArrayTypeInstance arrayOther = (JavaArrayTypeInstance) other;
            if (getNumArrayDimensions() != arrayOther.getNumArrayDimensions()) return false;
            return getArrayStrippedType().implicitlyCastsTo(arrayOther.getArrayStrippedType());
        }
        return false;
    }

    @Override
    public boolean canCastTo(JavaTypeInstance other) {
        return true;
    }

    @Override
    public String suggestVarName() {
        return "arr" + underlyingType.suggestVarName();
    }
}
