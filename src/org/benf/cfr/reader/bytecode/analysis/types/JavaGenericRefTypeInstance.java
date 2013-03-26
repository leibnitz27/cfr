package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 08:01
 */
public class JavaGenericRefTypeInstance implements JavaGenericBaseInstance {
    private final JavaTypeInstance typeInstance;
    private final List<JavaTypeInstance> genericTypes;
    private final ConstantPool cp;

    public JavaGenericRefTypeInstance(JavaTypeInstance typeInstance, List<JavaTypeInstance> genericTypes, ConstantPool cp) {
        this.typeInstance = typeInstance;
        this.cp = cp;
        this.genericTypes = genericTypes;
    }

    @Override
    public JavaTypeInstance getBoundInstance(GenericTypeBinder genericTypeBinder) {
        List<JavaTypeInstance> res = ListFactory.newList();
        for (JavaTypeInstance genericType : genericTypes) {
            res.add(genericTypeBinder.getBindingFor(genericType));
        }
        return new JavaGenericRefTypeInstance(typeInstance, res, cp);
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
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
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
    public JavaTypeInstance getDeGenerifiedType() {
        return typeInstance;
    }

    @Override
    public int getNumArrayDimensions() {
        return 0;
    }

    @Override
    public int hashCode() {
        return 31 + typeInstance.hashCode();
    }

    @Override
    public String getRawName() {
        return toString();
    }

    @Override
    public boolean isDirectInnerClassType(JavaTypeInstance possibleChild) {
        return typeInstance.isDirectInnerClassType(possibleChild);
    }

    public JavaTypeInstance getTypeInstance() {
        return typeInstance;
    }

    public String getClassName() {
        return typeInstance.getRawName();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof JavaGenericRefTypeInstance)) return false;
        JavaGenericRefTypeInstance other = (JavaGenericRefTypeInstance) o;
        return typeInstance.equals(other.typeInstance);
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
}
