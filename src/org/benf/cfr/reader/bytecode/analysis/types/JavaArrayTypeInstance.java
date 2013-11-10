package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

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
    public void dumpInto(Dumper d, TypeUsageInformation typeUsageInformation) {
        toCommonString(getNumArrayDimensions(), d);
    }

    @Override
    public String toString() {
        return new ToStringDumper().dump(this).toString();
    }

    private void toCommonString(int numDims, Dumper d) {
        d.dump(underlyingType.getArrayStrippedType());
        for (int x = 0; x < numDims; ++x) {
            d.print("[]");
        }
    }

    public void toVarargString(Dumper d) {
        toCommonString(getNumArrayDimensions() - 1, d);
        d.print(" ...");
    }

    @Override
    public String getRawName() {
        return new ToStringDumper().dump(this).toString();
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
        if (underlyingType instanceof JavaArrayTypeInstance) {
            return underlyingType.getArrayStrippedType();
        }
        return underlyingType;
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
    public void collectInto(TypeUsageCollector typeUsageCollector) {
        typeUsageCollector.collect(underlyingType);
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
