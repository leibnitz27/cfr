package org.benf.cfr.reader.bytecode.analysis.parse.utils.scope;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.Map;

public class ScopeDiscoverInfoCache {
    private final Map<StructuredStatement, Boolean> tests = MapFactory.newIdentityMap();

    public Boolean get(StructuredStatement structuredStatement) {
        return tests.get(structuredStatement);
    }

    public void put(StructuredStatement structuredStatement, Boolean b) {
        tests.put(structuredStatement, b);
    }

    boolean anyFound() {
        for (Boolean value : tests.values()) {
            if (value) return true;
        }
        return false;
    }
}
