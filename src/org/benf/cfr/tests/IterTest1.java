package org.benf.cfr.tests;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class IterTest1 {


    public boolean test1(List<String> list, Set<Integer> set) {
        boolean result = false;
        for (String o : list) {
            result |= set.add(Integer.parseInt(o));
        }
        return result;
    }


    public boolean test2(List<Object> list, Set<Object> set) {
        boolean result = false;
        Iterator<Object> e = list.iterator();
        while (e.hasNext()) {
            if (set.add(e.next())) {
                result = true;
            }
        }
        return result;
    }


}
