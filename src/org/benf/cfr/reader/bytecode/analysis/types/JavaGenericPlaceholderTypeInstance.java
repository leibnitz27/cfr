package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.ObfuscationTypeMap;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;

import java.util.List;
import java.util.Map;

public class JavaGenericPlaceholderTypeInstance implements JavaGenericBaseInstance {
    private final String className;
    private final ConstantPool cp;
    private final JavaTypeInstance bound;

    public JavaGenericPlaceholderTypeInstance(String className, ConstantPool cp) {
        this.className = className;
        this.cp = cp;
        this.bound = null;
    }

    private JavaGenericPlaceholderTypeInstance(String className, ConstantPool cp, JavaTypeInstance bound) {
        this.className = className;
        this.cp = cp;
        this.bound = bound;
    }

    public JavaGenericPlaceholderTypeInstance withBound(JavaTypeInstance bound) {
        if (bound == null) return this;
        return new JavaGenericPlaceholderTypeInstance(className, cp, bound);
    }

    @Override
    public JavaTypeInstance getBoundInstance(GenericTypeBinder genericTypeBinder) {
        return genericTypeBinder.getBindingFor(this);
    }

    @Override
    public JavaAnnotatedTypeInstance getAnnotatedInstance() {
        return new Annotated();
    }

    private class Annotated implements JavaAnnotatedTypeInstance {
        private final List<AnnotationTableTypeEntry> entries = ListFactory.newList();

        @Override
        public JavaAnnotatedTypeIterator pathIterator() {
            return new Iterator();
        }

        @Override
        public Dumper dump(Dumper d) {
            if (!entries.isEmpty()) {
                for (AnnotationTableTypeEntry entry : entries) {
                    entry.dump(d);
                    d.print(' ');
                }
            }
            d.print(className);
            return d;
        }

        private class Iterator extends JavaAnnotatedTypeIterator.BaseAnnotatedTypeIterator {
            @Override
            public void apply(AnnotationTableTypeEntry entry) {
                entries.add(entry);
            }
        }
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public boolean hasUnbound() {
        return true;
    }

    @Override
    public boolean hasL01Wildcard() {
        return false;
    }

    @Override
    public JavaTypeInstance getWithoutL01Wildcard() {
        return this;
    }

    @Override
    public List<JavaTypeInstance> getGenericTypes() {
        return ListFactory.<JavaTypeInstance>newImmutableList(this);
    }

    @Override
    public boolean hasForeignUnbound(ConstantPool cp, int depth, boolean noWildcard, Map<String, FormalTypeParameter> externals) {
        // can't do reference equality on cp, because some types might come from the second load.
        // This needs reworking.
        if (className.equals(MiscConstants.UNBOUND_GENERIC)) {
            return depth == 0 || noWildcard;
        }
        if (!cp.equals(this.cp)) return true;

        if (externals == null) return false;
        return !externals.containsKey(className);
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
    public void dumpInto(Dumper d, TypeUsageInformation typeUsageInformation) {
        d.print(this.toString());
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
    public String getRawName(IllegalIdentifierDump iid) {
        return getRawName();
    }

    @Override
    public InnerClassInfo getInnerClassHereInfo() {
        return InnerClassInfo.NOT;
    }

    @Override
    public BindingSuperContainer getBindingSupers() {
        return null;
//        throw new UnsupportedOperationException("Binding supers on placeholder");
    }

    @Override
    public JavaTypeInstance directImplOf(JavaTypeInstance other) {
        return bound == null ? null : bound.directImplOf(other);
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
        return this;
    }

    @Override
    public JavaTypeInstance getDeGenerifiedType() {
        return bound == null ? TypeConstants.OBJECT : bound.getDeGenerifiedType();
    }

    @Override
    public RawJavaType getRawTypeOfSimpleType() {
        return RawJavaType.REF;
    }

    @Override
    public void collectInto(TypeUsageCollector typeUsageCollector) {
    }

    @Override
    public boolean implicitlyCastsTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        if (other == TypeConstants.OBJECT) return true;
        if (other.equals(this)) return true;
        if (bound != null) {
            return bound.implicitlyCastsTo(other, gtb);
        }
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
        if (className.equals(MiscConstants.UNBOUND_GENERIC)) { return "obj"; }
        return className;
    }

    @Override
    public JavaTypeInstance deObfuscate(ObfuscationTypeMap obfuscationTypeMap) {
        return new JavaGenericPlaceholderTypeInstance(className, cp, obfuscationTypeMap.get(bound));
    }

    @Override
    public JavaGenericRefTypeInstance asGenericRefInstance(JavaTypeInstance other) {
        return null;
    }

    @Override
    public boolean isRaw() {
        return false;
    }
}
