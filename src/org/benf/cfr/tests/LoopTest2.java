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
public class LoopTest2 {


    public int testNested(List<Object> list, Set<Object> set) {
        int result = 0;
        for (Object j : list) {
            for (Object o : list) {
                for (Object o2 : set) {
                    if (o.equals(o2)) result++;
                }
            }
        }
        return result;
    }


}
