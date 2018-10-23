package org.benf.cfr.reader.util.collections;

import java.util.*;

public class ListFactory {
    public static <X> List<X> newList() {
        return new ArrayList<X>();
    }

    public static <X> List<X> newImmutableList(X... original) {
        return Arrays.asList(original);
    }

    public static <X> List<X> newList(X... original) {
        List<X> res = ListFactory.newList();
        Collections.addAll(res, original);
        return res;
    }

    public static <X> List<X> newList(Collection<X> original) {
        return new ArrayList<X>(original);
    }

    public static <X> List<X> newList(int size) {
        return new ArrayList<X>(size);
    }

    public static <X> LinkedList<X> newLinkedList() {
        return new LinkedList<X>();
    }

    public static <X> List<X> uniqueList(Collection<X> list) {
        List<X> res = ListFactory.newList();
        Set<X> tmp = SetFactory.newSet();
        for (X x : list) {
            if (tmp.add(x)) res.add(x);
        }
        return res;
    }
}
