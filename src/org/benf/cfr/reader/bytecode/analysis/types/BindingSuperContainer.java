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

    public static BindingSuperContainer POISON = new BindingSuperContainer(null, null);

    private final ClassFile thisClass;
    private final Map<JavaTypeInstance, JavaGenericRefTypeInstance> boundSuperClasses;

    public BindingSuperContainer(ClassFile thisClass, Map<JavaTypeInstance, JavaGenericRefTypeInstance> boundSuperClasses) {
        this.thisClass = thisClass;
        this.boundSuperClasses = boundSuperClasses;
    }

    public JavaTypeInstance getBoundAssignable(JavaGenericRefTypeInstance assignable, JavaGenericRefTypeInstance superType) {
        JavaRefTypeInstance baseKey = superType.getDeGenerifiedType();
        JavaRefTypeInstance assignableKey = assignable.getDeGenerifiedType();
        if (assignableKey.equals(baseKey)) {
            // Nothing we can do!
            return assignable;
        }

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
        return boundSuperClasses.containsKey(possBase);
    }
}
