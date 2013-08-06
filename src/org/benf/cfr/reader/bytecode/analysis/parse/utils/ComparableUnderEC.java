package org.benf.cfr.reader.bytecode.analysis.parse.utils;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 05/08/2013
 * Time: 22:01
 */
public interface ComparableUnderEC {
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint);
}
