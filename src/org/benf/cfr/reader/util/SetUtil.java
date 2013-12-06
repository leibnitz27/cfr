package org.benf.cfr.reader.util;

import java.util.Collection;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 05/08/2013
 * Time: 05:42
 */
public class SetUtil {
    public static <X> boolean hasIntersection(Set<? extends X> b, Collection<? extends X> a) {
        for (X x : a) {
            if (b.contains(x)) return true;
        }
        return false;
    }

    public static <X> Set<X> intersectionOrNull(Set<? extends X> a, Set<? extends X> b) {
        if (b.size() < a.size()) {
            Set<? extends X> tmp = a;
            a = b;
            b = tmp;
        }
        Set<X> res = null;
        for (X x : a) {
            if (b.contains(x)) {
                if (res == null) res = SetFactory.newSet();
                res.add(x);
            }
        }
        return res;
    }

    public static <X> Set<X> difference(Set<? extends X> a, Set<? extends X> b) {
        Set<X> res = SetFactory.newSet();
        for (X a1 : a) {
            if (!b.contains(a1)) res.add(a1);
        }
        for (X b1 : b) {
            if (!a.contains(b1)) res.add(b1);
        }
        return res;
    }
}
