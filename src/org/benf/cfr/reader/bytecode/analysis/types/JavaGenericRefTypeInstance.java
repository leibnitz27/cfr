package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.CommaHelp;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 08:01
 */
public class JavaGenericRefTypeInstance implements JavaGenericBaseInstance {
    private final JavaRefTypeInstance typeInstance;
    private final List<JavaTypeInstance> genericTypes;
    private final boolean hasUnbound;

    public JavaGenericRefTypeInstance(JavaTypeInstance typeInstance, List<JavaTypeInstance> genericTypes) {
        if (!(typeInstance instanceof JavaRefTypeInstance)) {
            throw new IllegalStateException("Generic sitting on top of non reftype");
        }
        this.typeInstance = (JavaRefTypeInstance) typeInstance;
        this.genericTypes = genericTypes;
        boolean unbound = false;
        for (JavaTypeInstance type : genericTypes) {
            if (type instanceof JavaGenericBaseInstance) {
                if (((JavaGenericBaseInstance) type).hasUnbound()) {
                    unbound = true;
                    break;
                }
            }
        }
        hasUnbound = unbound;
    }

    @Override
    public boolean hasUnbound() {
        return hasUnbound;
    }

    @Override
    public boolean hasForeignUnbound(ConstantPool cp) {
        if (!hasUnbound) return false;
        for (JavaTypeInstance type : genericTypes) {
            if (type instanceof JavaGenericBaseInstance) {
                if (((JavaGenericBaseInstance) type).hasForeignUnbound(cp)) return true;
            }
        }
        return false;
    }


    @Override
    public JavaGenericRefTypeInstance getBoundInstance(GenericTypeBinder genericTypeBinder) {
        if (genericTypeBinder == null) {
            return this;
        }
        List<JavaTypeInstance> res = ListFactory.newList();
        for (JavaTypeInstance genericType : genericTypes) {
            res.add(genericTypeBinder.getBindingFor(genericType));
        }
        return new JavaGenericRefTypeInstance(typeInstance, res);
    }

    @Override
    public boolean tryFindBinding(JavaTypeInstance other, GenericTypeBinder target) {
        if (other instanceof JavaGenericRefTypeInstance) {
            // We can dig deeper.
            JavaGenericRefTypeInstance otherJavaGenericRef = (JavaGenericRefTypeInstance) other;
            if (genericTypes.size() == otherJavaGenericRef.genericTypes.size()) {
                for (int x = 0; x < genericTypes.size(); ++x) {
                    JavaTypeInstance genericType = genericTypes.get(x);
                    if (genericType instanceof JavaGenericBaseInstance) {
                        JavaGenericBaseInstance genericBaseInstance = (JavaGenericBaseInstance) genericType;
                        return genericBaseInstance.tryFindBinding(otherJavaGenericRef.genericTypes.get(x), target);
                    }
                }
            }
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
        sb.append(typeInstance.toString());
        sb.append("<");
        boolean first = true;
        for (JavaTypeInstance type : genericTypes) {
            first = CommaHelp.comma(first, sb);
            sb.append(type.toString());
        }
        sb.append(">");
        return sb.toString();
    }

    @Override
    public JavaTypeInstance getArrayStrippedType() {
        return this;
    }

    public List<JavaTypeInstance> getGenericTypes() {
        return genericTypes;
    }

    @Override
    public JavaRefTypeInstance getDeGenerifiedType() {
        return typeInstance;
    }

    @Override
    public int getNumArrayDimensions() {
        return 0;
    }

    @Override
    public int hashCode() {
        int hash = 31 + typeInstance.hashCode();
        return hash;
    }

    @Override
    public String getRawName() {
        return toString();
    }

    @Override
    public InnerClassInfo getInnerClassHereInfo() {
        return typeInstance.getInnerClassHereInfo();
    }

    public JavaTypeInstance getTypeInstance() {
        return typeInstance;
    }

    @Override
    public BindingSuperContainer getBindingSupers() {
        return typeInstance.getBindingSupers();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof JavaGenericRefTypeInstance)) return false;
        JavaGenericRefTypeInstance other = (JavaGenericRefTypeInstance) o;
        if (!typeInstance.equals(other.typeInstance)) return false;
        if (genericTypes.size() != other.genericTypes.size()) return false;
        for (int x = 0, len = genericTypes.size(); x < len; ++x) {
            if (!genericTypes.get(x).equals(other.genericTypes.get(x))) return false;
        }
        return true;
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
    public RawJavaType getRawTypeOfSimpleType() {
        return RawJavaType.REF;
    }

    @Override
    public boolean implicitlyCastsTo(JavaTypeInstance other) {
        if (other == TypeConstants.OBJECT) return true;
        if (this.equals(other)) return true;
        BindingSuperContainer bindingSuperContainer = getBindingSupers();
        if (bindingSuperContainer == null) return false;
        JavaTypeInstance degenerifiedOther = other.getDeGenerifiedType();
        JavaTypeInstance degenerifiedThis = getDeGenerifiedType();
        if (degenerifiedThis.equals(other)) return true;

        if (!bindingSuperContainer.containsBase(degenerifiedOther)) return false;
        JavaTypeInstance boundBase = bindingSuperContainer.getBoundSuperForBase(degenerifiedOther);
        if (other.equals(boundBase)) return true;
        if (degenerifiedOther.equals(other)) return true;
        return false;
    }

    @Override
    public boolean canCastTo(JavaTypeInstance other) {
        return true;
    }

    @Override
    public String suggestVarName() {
        return typeInstance.suggestVarName();
    }
}
