package org.benf.cfr.reader.util;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

/**
 * Created:
 * User: lee
 * Date: 27/04/2012
 */
public class Functional {
    public static <X> List<X> filter(List<X> input, Predicate<X> predicate) {
        List<X> result = ListFactory.newList();
        for (X item : input) {
            if (predicate.test(item)) result.add(item);
        }
        return result;
    }

    public static <X> Pair<List<X>, List<X>> partition(List<X> input, Predicate<X> predicate) {
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


    public static <X, Y> List<Y> map(List<X> input, UnaryFunction<X, Y> function) {
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

    public static <Y, X> Map<Y, List<X>> groupToMapBy(List<X> input, UnaryFunction<X, Y> mapF) {
        Map<Y, List<X>> temp = MapFactory.newMap();
        for (X x : input) {
            Y key = mapF.invoke(x);
            List<X> lx = temp.get(key);
            if (lx == null) {
                lx = ListFactory.newList();
                temp.put(key, lx);
            }
            lx.add(x);
        }
        return temp;
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
