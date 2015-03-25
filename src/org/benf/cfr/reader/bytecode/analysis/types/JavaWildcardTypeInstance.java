package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

import java.util.List;

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

    public JavaTypeInstance getUnderlyingType() {
        return underlyingType;
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
    public boolean hasUnbound() {
        if (underlyingType instanceof JavaGenericBaseInstance) {
            return ((JavaGenericBaseInstance) underlyingType).hasUnbound();
        }
        return false;
    }

    @Override
    public boolean hasForeignUnbound(ConstantPool cp) {
        if (underlyingType instanceof JavaGenericBaseInstance) {
            return ((JavaGenericBaseInstance) underlyingType).hasForeignUnbound(cp);
        }
        return false;
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public List<JavaTypeInstance> getGenericTypes() {
        if (underlyingType instanceof JavaGenericBaseInstance) {
            return ((JavaGenericBaseInstance) underlyingType).getGenericTypes();
        }
        return ListFactory.newList();
    }

    @Override
    public void dumpInto(Dumper d, TypeUsageInformation typeUsageInformation) {
        d.print("? ").print(wildcardType.toString()).print(' ');
        d.dump(underlyingType);
    }

    @Override
    public String toString() {
        return new ToStringDumper().dump(this).toString();
    }

    @Override
    public String getRawName() {
        return toString();
    }

    @Override
    public void collectInto(TypeUsageCollector typeUsageCollector) {
        underlyingType.collectInto(typeUsageCollector);
    }

    @Override
    public InnerClassInfo getInnerClassHereInfo() {
        return underlyingType.getInnerClassHereInfo();
    }

    @Override
    public BindingSuperContainer getBindingSupers() {
        return underlyingType.getBindingSupers();
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

    @Override
    public boolean implicitlyCastsTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        return false;
    }

    @Override
    public boolean canCastTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        return true;
    }

    @Override
    public String suggestVarName() {
        return null;
    }
}
