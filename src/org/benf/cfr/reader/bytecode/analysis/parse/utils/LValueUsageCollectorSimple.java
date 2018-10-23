package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.Collection;
import java.util.Set;

public class LValueUsageCollectorSimple implements LValueUsageCollector {
    private final Set<LValue> used = SetFactory.newSet();

    @Override
    public void collect(LValue lValue) {
        used.add(lValue);
    }

    public Collection<LValue> getUsedLValues() {
        return used;
    }

    public boolean isUsed(LValue lValue) {
        return used.contains(lValue);
    }
}
