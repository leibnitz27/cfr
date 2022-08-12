package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.annotations.AnnotationTableEntry;
import org.benf.cfr.reader.state.ObfuscationTypeMap;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;
import org.benf.cfr.reader.util.output.ToStringDumper;
import org.benf.cfr.reader.util.output.TypeContext;

import java.util.List;

public class JavaArrayTypeInstance implements JavaTypeInstance {
    private final int dimensions;
    private final JavaTypeInstance underlyingType;
    private JavaTypeInstance cachedDegenerifiedType;

    public JavaArrayTypeInstance(int dimensions, JavaTypeInstance underlyingType) {
        this.dimensions = dimensions;
        this.underlyingType = underlyingType;
    }

    public boolean isVarArgs() { return false; }

    // Return a short lived copy of this type which knows it is varargs.
    // Do not retain.
    public JavaArrayTypeInstance getVarArgTweak() {
        return new JavaArrayTypeInstance(dimensions, underlyingType) {
            @Override
            public boolean isVarArgs() {
                return true;
            }
        };
    }

    class Annotated implements JavaAnnotatedTypeInstance {
        private final List<List<AnnotationTableEntry>> entries;
        private final JavaAnnotatedTypeInstance annotatedUnderlyingType;

        Annotated() {
            entries = ListFactory.newList();
            for (int x=0;x<dimensions;++x) {
                entries.add(ListFactory.<AnnotationTableEntry>newList());
            }
            annotatedUnderlyingType = underlyingType.getAnnotatedInstance();
        }

        @Override
        public Dumper dump(Dumper d) {
            annotatedUnderlyingType.dump(d);
            java.util.Iterator<List<AnnotationTableEntry>> entryIterator = entries.iterator();
            while (entryIterator.hasNext()) {
                List<AnnotationTableEntry> entry = entryIterator.next();
                if (!entry.isEmpty()) {
                    d.print(' ');
                    for (AnnotationTableEntry oneEntry : entry) {
                        oneEntry.dump(d);
                        d.print(' ');
                    }
                }
                d.print(isVarArgs() && !entryIterator.hasNext() ? "..." : "[]");
            }
            return d;
        }

        @Override
        public JavaAnnotatedTypeIterator pathIterator() {
            return new Iterator();
        }

        private class Iterator extends JavaAnnotatedTypeIterator.BaseAnnotatedTypeIterator {
            private int curIdx;

            private Iterator() {
                curIdx = 0;
            }

            private Iterator(int idx) {
                curIdx = idx;
            }

            @Override
            public JavaAnnotatedTypeIterator moveArray(DecompilerComments comments) {
                if (curIdx+1 < dimensions) return new Iterator(curIdx+1);
                return annotatedUnderlyingType.pathIterator();
            }

            @Override
            public void apply(AnnotationTableEntry entry) {
                entries.get(curIdx).add(entry);
            }
        }
    }

    @Override
    public JavaAnnotatedTypeInstance getAnnotatedInstance() {
        return new Annotated();
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }

    @Override
    public void dumpInto(Dumper d, TypeUsageInformation typeUsageInformation, TypeContext typeContext) {
        if (isVarArgs()) {
            toCommonString(getNumArrayDimensions() - 1, d);
            d.print(" ...");
        } else {
            toCommonString(getNumArrayDimensions(), d);
        }
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

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public String getRawName() {
        return new ToStringDumper().dump(this).toString();
    }

    @Override
    public String getRawName(IllegalIdentifierDump iid) {
        // shouldn't ever need to get raw name of an array type for decoration purposes.
        return getRawName();
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
    public boolean isRaw() {
        return false;
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
        return RawJavaType.REF;
    }

    @Override
    public void collectInto(TypeUsageCollector typeUsageCollector) {
        typeUsageCollector.collect(underlyingType);
    }

    @Override
    public boolean implicitlyCastsTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        if (other == TypeConstants.OBJECT) return true;
        if (other instanceof JavaArrayTypeInstance) {
            JavaArrayTypeInstance arrayOther = (JavaArrayTypeInstance) other;
            if (getNumArrayDimensions() != arrayOther.getNumArrayDimensions()) return false;
            return getArrayStrippedType().implicitlyCastsTo(arrayOther.getArrayStrippedType(), gtb);
        }
        return false;
    }

    @Override
    public boolean impreciseCanCastTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        return true;
    }

    // Todo.... fix bearing in mind BoxingTest37
    @Override
    public boolean correctCanCastTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        return impreciseCanCastTo(other, gtb);
    }

    @Override
    public String suggestVarName() {
        return underlyingType.suggestVarName() + "Array";
    }

    @Override
    public JavaGenericRefTypeInstance asGenericRefInstance(JavaTypeInstance other) {
        return null;
    }

    @Override
    public JavaTypeInstance deObfuscate(ObfuscationTypeMap obfuscationTypeMap) {
        return new JavaArrayTypeInstance(dimensions, obfuscationTypeMap.get(underlyingType));
    }

    @Override
    public JavaTypeInstance directImplOf(JavaTypeInstance other) {
        return null;
    }
}
