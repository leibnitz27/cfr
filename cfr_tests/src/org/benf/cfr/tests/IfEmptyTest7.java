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
public class IfEmptyTest7 {

    /* 0_6 has a problem with empty blocks */

    public boolean test1(List<Object> list, Set<Object> set) {
        if (list == null) {
//            System.out.println("A");
            if (set == null) {
                System.out.println("B");
            } else {
            }
        } else if (set == null) {
//            System.out.println("D");
            if (list.isEmpty()) {
            } else {
                System.out.println("E");
            }
        }


        return true;
    }


}
