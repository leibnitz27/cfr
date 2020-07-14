package org.benf.cfr.reader.bytecode.analysis.loc;

import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class BytecodeLocSet extends BytecodeLoc {
    private final Map<Method, Set<Integer>> locs;

    BytecodeLocSet(Map<Method, Set<Integer>> locs) {
        this.locs = locs;
    }

    @Override
    void addTo(BytecodeLocCollector collector) {
        for (Map.Entry<Method, Set<Integer>> entry : locs.entrySet()) {
            collector.add(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Method, Set<Integer>> entry : locs.entrySet()) {
            sb.append(entry.getKey().getName()).append("[");
            for (Integer i : entry.getValue()) {
                sb.append(i).append(",");
            }
            sb.append("]");
        }
        return sb.toString();
    }

    @Override
    public Collection<Method> getMethods() {
        return locs.keySet();
    }

    @Override
    public Collection<Integer> getOffsetsForMethod(Method method) {
        return locs.get(method);
    }
}
