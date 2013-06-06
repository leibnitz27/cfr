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
    private final Map<JavaTypeInstance, JavaGenericRefTypeInstance> boundSupers;

    public BoundSuperCollector(ClassFile classFile) {
        this.classFile = classFile;
        this.boundSupers = MapFactory.newMap();
    }

    public BindingSuperContainer getBoundSupers() {
        return new BindingSuperContainer(classFile, boundSupers);
    }

    public void collect(JavaGenericRefTypeInstance boundBase) {
        JavaGenericRefTypeInstance prev = boundSupers.put(boundBase.getDeGenerifiedType(), boundBase);
    }

    public void collect(JavaRefTypeInstance boundBase) {
        JavaGenericRefTypeInstance prev = boundSupers.put(boundBase, null);
    }
}
