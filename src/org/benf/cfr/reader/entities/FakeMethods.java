package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.List;
import java.util.Map;

public class FakeMethods implements TypeUsageCollectable {
    private final Map<Object, FakeMethod> fakes = MapFactory.newOrderedMap();
    private final Map<String, Integer> nameCounts = MapFactory.newLazyMap(new UnaryFunction<String, Integer>() {
        @Override
        public Integer invoke(String arg) {
            return 0;
        }
    });

    public FakeMethod add(Object key, String nameHint, UnaryFunction<String, FakeMethod> methodFactory) {
        FakeMethod method = fakes.get(key);
        if (method == null) {
            Integer idx = nameCounts.get(nameHint);
            nameCounts.put(nameHint, idx+1);
            nameHint = "cfr_" + nameHint + "_" + idx;
            method = methodFactory.invoke(nameHint);
            fakes.put(key, method);
        }
        return method;
    }

    public List<FakeMethod> getMethods() {
        return ListFactory.newList(fakes.values());
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        for (FakeMethod method : fakes.values()) {
            collector.collectFrom(method);
        }
    }
}
