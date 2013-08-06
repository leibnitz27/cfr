package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;

import java.util.Collection;
import java.util.Iterator;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/08/2013
 * Time: 05:40
 */
public class DefaultEquivalenceConstraint implements EquivalenceConstraint {

    @Override
    public boolean equivalent(Object o1, Object o2) {
        if (o1 == null) return o2 == null;
        return o1.equals(o2);
    }

    @Override
    public boolean equivalent(ComparableUnderEC o1, ComparableUnderEC o2) {
        if (o1 == null) return o2 == null;
        return o1.equivalentUnder(o2, this);
    }

    @Override
    public boolean equivalent(Collection<? extends ComparableUnderEC> o1, Collection<? extends ComparableUnderEC> o2) {
        if (o1 == null) return o2 == null;
        if (o1.size() != o2.size()) return false;
        Iterator<? extends ComparableUnderEC> i1 = o1.iterator();
        Iterator<? extends ComparableUnderEC> i2 = o2.iterator();
        while (i1.hasNext()) {
            ComparableUnderEC c1 = i1.next();
            ComparableUnderEC c2 = i2.next();
            if (!c1.equivalentUnder(c2, this)) return false;
        }
        return true;
    }

}
