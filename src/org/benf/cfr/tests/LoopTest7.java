package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class LoopTest7 {

    char[] foo;

    public void test(int end) {
        char ch;
        int x = 0;
        while ((ch = foo[x]) != '*') {
            System.out.println("" + x++ + ": " + ch);
        }
    }

}
