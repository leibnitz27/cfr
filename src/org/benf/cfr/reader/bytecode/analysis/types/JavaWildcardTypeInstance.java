package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.ListFactory;
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

    @Override
    public JavaAnnotatedTypeInstance getAnnotatedInstance() {
        return new Annotated();
    }

    private class Annotated implements JavaAnnotatedTypeInstance {
        private final List<AnnotationTableTypeEntry> entries = ListFactory.newList();
        private final JavaAnnotatedTypeInstance underlyingAnnotated;

        private Annotated() {
            underlyingAnnotated = underlyingType.getAnnotatedInstance();
        }

        @Override
        public JavaAnnotatedTypeIterator pathIterator() {
            return new Iterator();
        }

        @Override
        public Dumper dump(Dumper d) {
            for (AnnotationTableTypeEntry entry : entries) {
                entry.dump(d);
                d.print(' ');
            }
            d.print("? ").print(wildcardType.toString()).print(' ');
            underlyingAnnotated.dump(d);
            return d;
        }

        private class Iterator extends JavaAnnotatedTypeIterator.BaseAnnotatedTypeIterator {

            @Override
            public JavaAnnotatedTypeIterator moveBound(DecompilerComments comments) {
                return underlyingAnnotated.pathIterator();
            }

            @Override
            public void apply(AnnotationTableTypeEntry entry) {
                entries.add(entry);
            }
        }

    }

    @Override
    public boolean hasL01Wildcard() {
        return true;
    }

    @Override
    public JavaTypeInstance getWithoutL01Wildcard() {
        return underlyingType;
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
    public boolean hasForeignUnbound(ConstantPool cp, int depth, boolean noWildcard) {
        if (underlyingType instanceof JavaGenericBaseInstance) {
            return ((JavaGenericBaseInstance) underlyingType).hasForeignUnbound(cp, depth, noWildcard);
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
        return underlyingType;
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
    public boolean impreciseCanCastTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        return true;
    }

    @Override
    public boolean correctCanCastTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        return impreciseCanCastTo(other, gtb);
    }

    @Override
    public String suggestVarName() {
        return null;
    }
}
