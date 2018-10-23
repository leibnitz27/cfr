package org.benf.cfr.reader.util.collections;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class SetUtil {
    public static <X> boolean equals (Set<? extends X> b, Collection<? extends X> a) {
        if (a.size() != b.size()) return false;
        for (X x : a) {
            if (!b.contains(x)) return false;
        }
        return true;
    }

    public static <X> boolean hasIntersection(Set<? extends X> b, Collection<? extends X> a) {
        if (a.isEmpty() || b.isEmpty()) return false;
        for (X x : a) {
            if (b.contains(x)) return true;
        }
        return false;
    }

    // Note - this could return the original set, so don't use it if you want to mutate the set!
    public static <X> Set<X> originalIntersectionOrNull(Set<X> a, Set<? extends X> b) {
        if (a==null||b==null) return null;
        if (a.equals(b)) return a;
        return intersectionOrNull(a,b);
    }

    public static <X> Set<X> intersectionOrNull(Set<? extends X> a, Set<? extends X> b) {
        if (a==null||b==null) return null;
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

    public static <X> List<X> differenceAtakeBtoList(Set<? extends X> a, Set<? extends X> b) {
        List<X> res = ListFactory.newList();
        for (X a1 : a) {
            if (!b.contains(a1)) res.add(a1);
        }
        return res;
    }

    public static <X> X getSingle(Set<? extends X> a) {
        return a.iterator().next();
    }
}
