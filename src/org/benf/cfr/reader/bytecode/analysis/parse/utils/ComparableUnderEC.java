package org.benf.cfr.reader.bytecode.analysis.parse.utils;

public interface ComparableUnderEC {
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint);
}
