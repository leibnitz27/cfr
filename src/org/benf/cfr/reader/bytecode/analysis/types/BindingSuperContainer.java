package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.ClassFile;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 05/06/2013
 * Time: 06:46
 */
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

    public JavaGenericRefTypeInstance getBoundSuperForBase(JavaTypeInstance possBase) {
        if (!(possBase instanceof JavaRefTypeInstance)) return null;
        return boundSuperClasses.get(possBase);
    }

    public Map<JavaRefTypeInstance, Route> getBoundSuperRoute() {
        return boundSuperRoute;
    }
}
