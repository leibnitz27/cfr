package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 07/06/2011
 * Time: 06:24
 * To change this template use File | Settings | File Templates.
 */
public class CondJumpTest4 {

    public void test(int a) {
        if (a == 1) {
            System.out.println("One");
        } else if (a == 2 || a == 3) {
            System.out.println("2/3");
        } else if (a <= 5) {
            System.out.println("4/5");
        } else if (a == 6) {
            System.out.println("6");
        }
        System.out.println("Done");
    }
}
