package org.benf.cfr.reader.util;

import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 09/03/2012
 * Time: 17:51
 * To change this template use File | Settings | File Templates.
 */
public class MapFactory {
    public static <X extends Object, Y extends Object> Map<X, Y> newMap() {
        return new HashMap<X, Y>();
    }

    public static <X extends Object, Y extends Object> Map<X, Y> newIdentityMap() {
        return new IdentityHashMap<X, Y>();
    }

    public static <X extends Object, Y extends Object> TreeMap<X, Y> newTreeMap() {
        return new TreeMap<X, Y>();
    }

    public static <X extends Object, Y extends Object> Map<X, Y> newLinkedMap() {
        return new LinkedHashMap<X, Y>();
    }

    public static <X extends Object, Y extends Object> Map<X, Y> newLazyMap(UnaryFunction<X, Y> factory) {
        return new LazyMap<X, Y>(MapFactory.<X, Y>newMap(), factory);
    }

    public static <X extends Object, Y extends Object> Map<X, Y> newLinkedLazyMap(UnaryFunction<X, Y> factory) {
        return new LazyMap<X, Y>(MapFactory.<X, Y>newLinkedMap(), factory);
    }

    public static <X extends Object, Y extends Object> Map<X, Y> newLazyMap(Map<X, Y> base, UnaryFunction<X, Y> factory) {
        return new LazyMap<X, Y>(base, factory);
    }

    public static <X extends Object, Y extends Object> Map<X, Y> newExceptionRetainingLazyMap(UnaryFunction<X, Y> factory) {
        return new LazyExceptionRetainingMap<X, Y>(MapFactory.<X, Y>newMap(), factory);
    }

    public static <X extends Object, Y extends Object> Map<X, Y> newExceptionRetainingLazyMap(Map<X, Y> base, UnaryFunction<X, Y> factory) {
        return new LazyExceptionRetainingMap<X, Y>(base, factory);
    }

}
