package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import java.util.Collection;

public interface EquivalenceConstraint {
    boolean equivalent(Object o1, Object o2);

    boolean equivalent(Collection o1, Collection o2);

    boolean equivalent(ComparableUnderEC o1, ComparableUnderEC o2);
}
