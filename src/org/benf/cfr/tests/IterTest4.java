package org.benf.cfr.tests;

import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class IterTest4 {


    public boolean test1(List<String> list, Set<Integer> set) {
        boolean result = false;
        for (String o : list) {
            result |= set.add(Integer.parseInt(o));
        }
        return result;
    }
}
