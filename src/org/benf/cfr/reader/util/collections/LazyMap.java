package org.benf.cfr.reader.util.collections;

import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class LazyMap<X, Y> implements Map<X, Y> {
    private final Map<X, Y> inner;
    private final UnaryFunction<X, Y> factory;

    public LazyMap(Map<X, Y> inner, UnaryFunction<X, Y> factory) {
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
            //noinspection unchecked
            X x = (X) o;
            res = factory.invoke(x);
            inner.put(x, res);
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

    public Y getWithout(X x) {
        return inner.get(x);
    }
}
