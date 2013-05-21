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
public class IterTest5 {


    public boolean test1(List<String> list, Set<Integer> set) {
        boolean result = false;
        int x = 3;
        int y = 1; // you can't tell this is an int.
        for (x = 1; x < 12; ++x) {    // use of an itervar already in scope.
            System.out.println(x);
        }
        return result;
    }
}
