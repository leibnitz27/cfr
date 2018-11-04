package org.benf.cfr.reader.util.collections;

import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.Map;

public class LazyExceptionRetainingMap<X, Y> extends LazyMap<X, Y> {
    private final Map<X, RuntimeException> exceptionMap = MapFactory.newMap();

    LazyExceptionRetainingMap(Map<X, Y> inner, UnaryFunction<X, Y> factory) {
        super(inner, factory);
    }

    @Override
    public Y get(Object o) {
        RuntimeException exception = exceptionMap.get(o);
        if (exception == null) {
            try {
                return super.get(o);
            } catch (RuntimeException e) {
                exception = e;
                //noinspection unchecked
                exceptionMap.put((X) o, e);
            }
        }
        throw exception;
    }
}
