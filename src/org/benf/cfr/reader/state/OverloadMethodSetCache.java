package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.Map;

// The cost of retaining all overload information may become large.  Keeping it centrally allows us to flush it if in low
// memory mode.
public class OverloadMethodSetCache {
    private final Map<ClassFile, Map<MethodPrototype, OverloadMethodSet>> content = MapFactory.newLazyMap(new UnaryFunction<ClassFile, Map<MethodPrototype, OverloadMethodSet>>() {
        @Override
        public Map<MethodPrototype, OverloadMethodSet> invoke(ClassFile arg) {
            return MapFactory.newIdentityMap();
        }
    });

    public OverloadMethodSet get(ClassFile classFile, MethodPrototype methodPrototype) {
        return content.get(classFile).get(methodPrototype);
    }

    public void set(ClassFile classFile, MethodPrototype methodPrototype, OverloadMethodSet overloadMethodSet) {
        content.get(classFile).put(methodPrototype, overloadMethodSet);
    }
}
