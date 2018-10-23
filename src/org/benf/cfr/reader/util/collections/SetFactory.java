package org.benf.cfr.reader.util.collections;

import java.util.*;

@SuppressWarnings("unused")
public class SetFactory {
    public static <X> Set<X> newSet() {
        return new HashSet<X>();
    }

    public static <X> Set<X> newSortedSet() {
        return new TreeSet<X>();
    }

    public static <X extends Enum<X>> EnumSet<X> newSet(EnumSet<X> content) {
        return EnumSet.copyOf(content);
    }

    public static <X> Set<X> newSet(Collection<X> content) {
        return new HashSet<X>(content);
    }

    public static <X> Set<X> newSet(Collection<X> content, Collection<X> otherContent) {
        HashSet<X> res = new HashSet<X>(content);
        res.addAll(otherContent);
        return res;
    }

    public static <X> Set<X> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<X, Boolean>());
    }

    public static <X> Set<X> newSet(X... content) {
        Set<X> res = new HashSet<X>();
        Collections.addAll(res, content);
        return res;
    }

    public static <X> Set<X> newOrderedSet() {
        return new LinkedHashSet<X>();
    }

    public static <X> Set<X> newOrderedSet(Collection<X> content) {
        return new LinkedHashSet<X>(content);
    }

    public static <X> Set<X> newOrderedSet(X... content) {
        Set<X> res = new LinkedHashSet<X>();
        Collections.addAll(res, content);
        return res;
    }

}
