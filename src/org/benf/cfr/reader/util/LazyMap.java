package org.benf.cfr.reader.util;

import org.benf.cfr.reader.util.functors.NonaryFunction;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 23/06/2012
 * Time: 10:32
 */
public class LazyMap<X, Y> implements Map<X, Y> {
    private final Map<X, Y> inner;
    private final NonaryFunction<Y> factory;

    public LazyMap(Map<X, Y> inner, NonaryFunction<Y> factory) {
        this.inner = inner;
        this.factory = factory;
    }

    @Override
    public int size() {
        return inner.size();
    }

    @Override
    public boolean isEmpty() {
        return inner.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return inner.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return inner.containsValue(o);
    }

    @Override
    public Y get(Object o) {
        Y res = inner.get(o);
        if (res == null) {
            res = factory.invoke();
            inner.put((X) o, res);
        }
        return res;
    }

    @Override
    public Y put(X x, Y y) {
        return inner.put(x, y);
    }

    @Override
    public Y remove(Object o) {
        return inner.remove(o);
    }

    @Override
    public void putAll(Map<? extends X, ? extends Y> map) {
        inner.putAll(map);
    }

    @Override
    public void clear() {
        inner.clear();
    }

    @Override
    public Set<X> keySet() {
        return inner.keySet();
    }

    @Override
    public Collection<Y> values() {
        return inner.values();
    }

    @Override
    public Set<Entry<X, Y>> entrySet() {
        return inner.entrySet();
    }
}
