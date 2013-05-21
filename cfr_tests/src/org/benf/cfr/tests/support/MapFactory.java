package org.benf.cfr.tests.support;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/05/2013
 * Time: 14:13
 */
public class MapFactory {
    public static <X extends Object, Y extends Object> Map<X, Y> newMap() {
        return new HashMap<X, Y>();
    }

    public static <X extends Object, Y extends Object> TreeMap<X, Y> newTreeMap() {
        return new TreeMap<X, Y>();
    }

    public static <X extends Object, Y extends Object> Map<X, Y> newOrderedMap() {
        return new LinkedHashMap<X, Y>();
    }

}
