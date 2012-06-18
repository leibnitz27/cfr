package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class LoopTest5 {

    int y;

    public void test5(int end) {
        for (int x = 0; (x = x + 2) < end; ) {
            System.out.println(x);
        }
    }

    public void test6(int end) {
        for (int x = 3; x > 0; x--) {
            System.out.println(x);
        }
    }

    public void test7(int end) {
        for (int x = 0; x < end; ++x) {
            System.out.println(x);
        }
    }


    public void test8(int end) {
        for (y = 0; y != end; ++y) {
            System.out.println(y);
        }
    }
}
