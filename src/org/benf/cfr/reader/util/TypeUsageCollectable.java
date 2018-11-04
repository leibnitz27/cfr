package org.benf.cfr.reader.util;

import org.benf.cfr.reader.state.TypeUsageCollector;

public interface TypeUsageCollectable {
    void collectTypeUsages(TypeUsageCollector collector);
}
