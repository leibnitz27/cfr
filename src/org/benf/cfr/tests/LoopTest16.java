package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class LoopTest16 {

    char[] foo;

    public void test(int end) {
        char ch;
        for (int x = 0, y = 0; x < 10; x++, y += 2) {
            System.out.println("x" + x + "y" + y);
        }
    }

}
