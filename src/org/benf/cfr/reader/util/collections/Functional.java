package org.benf.cfr.reader.util.collections;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

public class Functional {

    public static class NotNull<X> implements Predicate<X> {
        @Override
        public boolean test(X in) {
            return in != null;
        }
    }

    public static <X> List<X> filterColl(Collection<X> input, Predicate<X> predicate) {
        return filter(input, predicate);
    }

    public static <X> List<X> filter(Collection<X> input, Predicate<X> predicate) {
        List<X> result = ListFactory.newList();
        for (X item : input) {
            if (predicate.test(item)) result.add(item);
        }
        return result;
    }


    public static <X> Set<X> filterSet(Collection<X> input, Predicate<X> predicate) {
        Set<X> result = SetFactory.newSet();
        for (X item : input) {
            if (predicate.test(item)) result.add(item);
        }
        return result;
    }

    public static <X> boolean any(Collection<X> input, Predicate<X> predicate) {
        List<X> result = ListFactory.newList();
        for (X item : input) {
            if (predicate.test(item)) return true;
        }
        return false;
    }

    public static <X> boolean all(Collection<X> input, Predicate<X> predicate) {
        List<X> result = ListFactory.newList();
        for (X item : input) {
            if (!predicate.test(item)) return false;
        }
        return true;
    }

    public static <X> Pair<List<X>, List<X>> partition(Collection<X> input, Predicate<X> predicate) {
        List<X> lTrue = ListFactory.newList();
        List<X> lFalse = ListFactory.newList();
        for (X item : input) {
            if (predicate.test(item)) {
                lTrue.add(item);
            } else {
                lFalse.add(item);
            }
        }
        return new Pair<List<X>, List<X>>(lTrue, lFalse);
    }


    public static <X, Y> List<Y> map(Collection<X> input, UnaryFunction<X, Y> function) {
        List<Y> result = ListFactory.newList();
        for (X item : input) {
            result.add(function.invoke(item));
        }
        return result;
    }

    public static <X> List<X> uniqAll(List<X> input) {
        Set<X> found = SetFactory.newSet();
        List<X> result = ListFactory.newList();
        for (X in : input) {
            if (found.add(in)) result.add(in);
        }
        return result;
    }

    public static <Y, X> Map<Y, List<X>> groupToMapBy(Collection<X> input, UnaryFunction<X, Y> mapF) {
        Map<Y, List<X>> temp = MapFactory.newMap();
        return groupToMapBy(input, temp, mapF);
    }

    public static <Y, X> Map<Y, List<X>> groupToMapBy(Collection<X> input, Map<Y, List<X>> tgt, UnaryFunction<X, Y> mapF) {
        for (X x : input) {
            Y key = mapF.invoke(x);
            List<X> lx = tgt.get(key);
            if (lx == null) {
                lx = ListFactory.newList();
                tgt.put(key, lx);
            }
            lx.add(x);
        }
        return tgt;
    }

    public static <Y, X> List<Y> groupBy(List<X> input, Comparator<? super X> comparator, UnaryFunction<List<X>, Y> gf) {
        TreeMap<X, List<X>> temp = new TreeMap<X, List<X>>(comparator);
        for (X x : input) {
            List<X> lx = temp.get(x);
            if (lx == null) {
                lx = ListFactory.newList();
                temp.put(x, lx);
            }
            lx.add(x);
        }
        List<Y> res = ListFactory.newList();
        for (List<X> lx : temp.values()) {
            res.add(gf.invoke(lx));
        }
        return res;
    }
}
