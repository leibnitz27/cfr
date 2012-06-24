package org.benf.cfr.reader.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 22/06/2012
 * Time: 07:00
 */
public class BiDiMultiMap<X, Y> implements Map<X, Y> {

    private final Map<X, Y> forwardMap = MapFactory.newMap();
    private final Map<Y, Set<X>> reverseMap = MapFactory.newMap();

    @Override
    public int size() {
        return forwardMap.size();
    }

    @Override
    public boolean isEmpty() {
        return forwardMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return forwardMap.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return forwardMap.containsValue(o);
    }

    @Override
    public Y get(Object o) {
        return forwardMap.get(o);
    }

    private void removeOldY(X x, Y oldY) {
        Set<X> oldRevMapSet = reverseMap.get(oldY);
        if (oldRevMapSet == null) return;
        if (oldRevMapSet.size() == 1) {
            reverseMap.remove(oldY);
        } else {
            oldRevMapSet.remove(x);
        }
    }

    @Override
    public Y put(X x, Y y) {
        Y oldY = forwardMap.put(x, y);
        removeOldY(x, oldY);

        Set<X> revMapSet = reverseMap.get(y);
        if (revMapSet == null) {
            revMapSet = SetFactory.newSet();
            reverseMap.put(y, revMapSet);
        }
        revMapSet.add(x);
        return oldY;
    }

    @Override
    public Y remove(Object o) {
        Y oldY = forwardMap.remove(o);
        if (oldY != null) removeOldY((X) o, oldY);
        return oldY;
    }

    @Override
    public void putAll(Map<? extends X, ? extends Y> map) {
        for (Map.Entry<? extends X, ? extends Y> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        forwardMap.clear();
        reverseMap.clear();
    }

    @Override
    public Set<X> keySet() {
        return forwardMap.keySet();
    }

    @Override
    public Collection<Y> values() {
        return forwardMap.values();
    }

    @Override
    public Set<Entry<X, Y>> entrySet() {
        return forwardMap.entrySet();
    }
}
