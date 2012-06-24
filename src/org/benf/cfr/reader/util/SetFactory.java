package org.benf.cfr.reader.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 09/03/2012
 * Time: 17:51
 * To change this template use File | Settings | File Templates.
 */
public class SetFactory {
    public static <X extends Object> Set<X> newSet() {
        return new HashSet<X>();
    }

    public static <X extends Object> Set<X> newSet(Collection<X> content) {
        return new HashSet<X>(content);
    }

    public static <X extends Object> Set<X> newSet(X... content) {
        Set<X> res = new HashSet<X>();
        for (X x : content) {
            res.add(x);
        }
        return res;
    }

}
