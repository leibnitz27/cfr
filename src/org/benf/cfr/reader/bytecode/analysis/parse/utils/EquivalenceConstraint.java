package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 05/08/2013
 * Time: 17:44
 */
public interface EquivalenceConstraint {
    boolean equivalent(Object o1, Object o2);

    boolean equivalent(ComparableUnderEC o1, ComparableUnderEC o2);

    boolean equivalent(Collection<? extends ComparableUnderEC> o1, Collection<? extends ComparableUnderEC> o2);

}
