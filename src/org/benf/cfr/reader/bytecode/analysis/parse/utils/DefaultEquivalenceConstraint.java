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

    public static final EquivalenceConstraint INSTANCE = new DefaultEquivalenceConstraint();

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
    public boolean equivalent(Collection col1, Collection col2) {
        if (col1 == null) return col2 == null;
        if (col1.size() != col2.size()) return false;
        Iterator<?> i1 = col1.iterator();
        Iterator<?> i2 = col2.iterator();
        while (i1.hasNext()) {
            Object o1 = i1.next();
            Object o2 = i2.next();
            if (o1 instanceof ComparableUnderEC && o2 instanceof ComparableUnderEC) {
                if (!equivalent((ComparableUnderEC) o1, (ComparableUnderEC) o2)) return false;
            } else {
                if (!equivalent(o1, o2)) return false;
            }
        }
        return true;
    }

}
