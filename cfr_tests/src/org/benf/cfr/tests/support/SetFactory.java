package org.benf.cfr.tests.support;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/05/2013
 * Time: 14:15
 */
public class SetFactory {
    public static <X extends Object> Set<X> newSet() {
        return new HashSet<X>();
    }

    public static <X extends Object> Set<X> newSet(Collection<X> content) {
        return new HashSet<X>(content);
    }

    public static <X extends Object> Set<X> newSet(X... content) {
        Set<X> res = new HashSet<X>();
        for (X x : content) {
            res.add(x);
        }
        return res;
    }

}
