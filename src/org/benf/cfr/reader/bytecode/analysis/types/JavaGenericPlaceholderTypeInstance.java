package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

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

    @Override
    public boolean hasUnbound() {
        return true;
    }

    @Override
    public List<JavaTypeInstance> getGenericTypes() {
        return ListFactory.<JavaTypeInstance>newList(this);
    }

    @Override
    public boolean hasForeignUnbound(ConstantPool cp) {
        // can't do reference equality on cp, because some types might come from the second load.
        // This needs reworking.
        if (className.equals(MiscConstants.UNBOUND_GENERIC)) return true;
        return !cp.equals(this.cp);
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
    public InnerClassInfo getInnerClassHereInfo() {
        return InnerClassInfo.NOT;
    }

    @Override
    public BindingSuperContainer getBindingSupers() {
        return null;
//        throw new UnsupportedOperationException("Binding supers on placeholder");
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
        return TypeConstants.OBJECT;
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
        return false;
    }

    @Override
    public boolean canCastTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        return true;
    }

    @Override
    public String suggestVarName() {
        return className;
    }
}
