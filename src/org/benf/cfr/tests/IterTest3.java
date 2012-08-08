package org.benf.cfr.tests;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class IterTest3<T> {


    public boolean test1(Map<T, Integer[]> map) {
        boolean result = false;
        for (Map.Entry<T, Integer[]> o : map.entrySet()) {
            result |= o.getKey().equals(o.getValue());
        }
        return result;
    }


}
