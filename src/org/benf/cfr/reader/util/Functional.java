package org.benf.cfr.reader.util;

import java.util.List;

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
}
