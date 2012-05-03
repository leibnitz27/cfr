package org.benf.cfr.reader.util;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
}
