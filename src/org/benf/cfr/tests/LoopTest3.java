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
public class LoopTest3 {


    public int testNested(List<Object> list, Set<Object> set) {
        int result = 0;
        int x = 0, y = 0;
        fred:
        for (Object o : list) {
            System.out.println("fred");
            jim:
            for (Object o2 : set) {
                System.out.println("jim");
                if (x == y) continue jim;
                continue fred;
            }
            break;
        }
        return result;
    }

//    public static void main (String args[]) {
//        List<Object> lst = ListFactory.newList((Object)"a", "b", "c");
//        Set<Object> set = SetFactory.newSet((Object)1, 2, 3);
//        LoopTest3 loopTest3 = new LoopTest3();
//        loopTest3.testNested(lst, set);
//    }
}
