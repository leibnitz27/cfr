package org.benf.cfr.reader.util;

import java.util.Collection;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 05/08/2013
 * Time: 05:42
 */
public class SetUtil {
    public static <X> boolean hasIntersection(Set<? extends X> b, Collection<? extends X> a) {
        for (X x : a) {
            if (b.contains(x)) return true;
        }
        return false;
    }
}
