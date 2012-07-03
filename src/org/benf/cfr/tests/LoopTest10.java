package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class LoopTest10 {

    char[] foo;

    public void test(int end) {
        int c = 0;
        while (++c < end || ++c < end) {
            System.out.println(":" + c);
        }
    }

    public void test2(int end) {
        int c = 0;
        while (c++ < end || ++c < end) {
            System.out.println(":" + c);
        }
    }
}
