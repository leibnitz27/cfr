package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;
import java.util.Map;

public class BindingSuperContainer {

    public enum Route {
        IDENTITY,
        EXTENSION,
        INTERFACE
    }

    public static BindingSuperContainer POISON = new BindingSuperContainer(null, null, null);

    private final ClassFile thisClass;
    private final Map<JavaRefTypeInstance, JavaGenericRefTypeInstance> boundSuperClasses;
    private final Map<JavaRefTypeInstance, Route> boundSuperRoute;

    public BindingSuperContainer(ClassFile thisClass, Map<JavaRefTypeInstance, JavaGenericRefTypeInstance> boundSuperClasses,
                                 Map<JavaRefTypeInstance, Route> boundSuperRoute) {
        this.thisClass = thisClass;
        this.boundSuperClasses = boundSuperClasses;
        this.boundSuperRoute = boundSuperRoute;
    }

    public JavaTypeInstance getBoundAssignable(JavaGenericRefTypeInstance assignable, JavaGenericRefTypeInstance superType) {
        JavaRefTypeInstance baseKey = superType.getDeGenerifiedType();
        JavaRefTypeInstance assignableKey = assignable.getDeGenerifiedType();
//        if (assignableKey.equals(baseKey)) {
//            // Nothing we can do!
//            return assignable;
//        }

        JavaGenericRefTypeInstance reboundBase = boundSuperClasses.get(baseKey);
        if (reboundBase == null) {
            // This shouldn't happen.
            return assignable;
        }
        GenericTypeBinder genericTypeBinder = GenericTypeBinder.extractBindings(reboundBase, superType);
        JavaGenericRefTypeInstance boundAssignable = assignable.getBoundInstance(genericTypeBinder);
        return boundAssignable;
    }

    public boolean containsBase(JavaTypeInstance possBase) {
        if (!(possBase instanceof JavaRefTypeInstance)) return false;
        return boundSuperClasses.containsKey(possBase);
    }

    public Map<JavaRefTypeInstance, JavaGenericRefTypeInstance> getBoundSuperClasses() {
        return boundSuperClasses;
    }

    /*
     * We want to figure out what type to call an anonymous type.
     * We can't refer to its type in the constructing method.
     *
     * boundSuperClasses contains the type heirachy in DFS.
     * 0 is the identity.
     * 1 is the super.  If super is object, then 2 is the first interface.
     *
     * Return super, unless Object, then first interface.
     * if first interface is invalid, return first super anyway.
     */
    public JavaTypeInstance getMostLikelyAnonymousType(JavaTypeInstance original) {
        List<JavaRefTypeInstance> orderedTypes = ListFactory.newList(boundSuperClasses.keySet());
        if (orderedTypes.isEmpty() || orderedTypes.size() == 1) return original;
        JavaRefTypeInstance candidate = orderedTypes.get(1);
        if (candidate.equals(TypeConstants.OBJECT)) {
            if (orderedTypes.size() >= 3) {
                candidate = orderedTypes.get(2);
            } else {
                return original;
            }
        }
        JavaTypeInstance generic = boundSuperClasses.get(candidate);
        if (generic == null) {
            return candidate;
        } else {
            return generic;
        }
    }

    public JavaGenericRefTypeInstance getBoundSuperForBase(JavaTypeInstance possBase) {
        if (!(possBase instanceof JavaRefTypeInstance)) return null;
        return boundSuperClasses.get(possBase);
    }

    public Map<JavaRefTypeInstance, Route> getBoundSuperRoute() {
        return boundSuperRoute;
    }
}
