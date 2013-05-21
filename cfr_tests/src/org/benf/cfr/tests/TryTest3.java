package org.benf.cfr.tests;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class TryTest3 {


    public void test1(int x, List<Integer> a) {
        for (Integer y : a) {
            try {
                if (x < 3) {
                    System.out.println("a");
                } else {
                    System.out.println("b");
                }
                continue;
            } catch (RuntimeException e) {
                System.out.println("e");
            }
        }
        System.out.print(5);
    }
}
