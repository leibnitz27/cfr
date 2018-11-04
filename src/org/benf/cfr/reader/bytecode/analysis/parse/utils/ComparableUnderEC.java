package org.benf.cfr.reader.bytecode.analysis.parse.utils;

public interface ComparableUnderEC {
    boolean equivalentUnder(Object o, EquivalenceConstraint constraint);
}
