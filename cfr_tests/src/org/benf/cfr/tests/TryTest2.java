package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class TryTest2 {


    public void test1(int x) {
        try {
            if (x < 3) {
                System.out.println("a");
            } else {
                System.out.println("b");
            }
        } catch (RuntimeException e) {
            System.out.println("e");
        }
        System.out.print(5);
    }
}
