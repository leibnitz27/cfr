package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 23/05/2013
 * Time: 17:45
 */
public class BoundSuperCollector {


    private final ClassFile classFile;
    private final Map<JavaRefTypeInstance, JavaGenericRefTypeInstance> boundSupers;
    private final Map<JavaRefTypeInstance, BindingSuperContainer.Route> boundSuperRoute;

    public BoundSuperCollector(ClassFile classFile) {
        this.classFile = classFile;
        this.boundSupers = MapFactory.newMap();
        this.boundSuperRoute = MapFactory.newMap();
    }

    public BindingSuperContainer getBoundSupers() {
        return new BindingSuperContainer(classFile, boundSupers, boundSuperRoute);
    }

    public void collect(JavaGenericRefTypeInstance boundBase, BindingSuperContainer.Route route) {
        JavaRefTypeInstance key = boundBase.getDeGenerifiedType();
        JavaGenericRefTypeInstance prev = boundSupers.put(key, boundBase);
        boundSuperRoute.put(key, route);

    }

    public void collect(JavaRefTypeInstance boundBase, BindingSuperContainer.Route route) {
        JavaGenericRefTypeInstance prev = boundSupers.put(boundBase, null);
        boundSuperRoute.put(boundBase, route);
    }
}
