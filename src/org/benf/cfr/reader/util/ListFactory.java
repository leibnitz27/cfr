package org.benf.cfr.reader.util;

import java.util.*;

public class ListFactory {
    public static <X extends Object> List<X> newList() {
        return new ArrayList<X>();
    }

    public static <X extends Object> List<X> newList(X... original) {
        return Arrays.asList(original);
    }

    public static <X extends Object> List<X> newList(Collection<X> original) {
        return new ArrayList<X>(original);
    }

    public static <X extends Object> List<X> newList(int size) {
        return new ArrayList<X>(size);
    }

    public static <X extends Object> LinkedList<X> newLinkedList() {
        return new LinkedList<X>();
    }

    public static <X extends Object> List<X> uniqueList(Collection<X> list) {
        List<X> res = ListFactory.newList();
        Set<X> tmp = SetFactory.newSet();
        for (X x : list) {
            if (tmp.add(x)) res.add(x);
        }
        return res;
    }
}
