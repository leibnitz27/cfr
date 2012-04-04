package org.benf.cfr.reader.util;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 09/03/2012
 * Time: 17:51
 * To change this template use File | Settings | File Templates.
 */
public class MapFactory {
    public static <X extends Object, Y extends  Object> Map<X,Y> newMap() {
        return new HashMap<X,Y>();
    }

    public static <X extends Object, Y extends  Object> TreeMap<X,Y> newTreeMap() {
        return new TreeMap<X,Y>();
    }
}
