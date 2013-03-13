package org.benf.cfr.tests;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 18/12/2012
 * Time: 07:22
 * <p/>
 * Test that default is lifted inside the switch
 */
public class ArgTest1 {

    private int foo(boolean a, boolean b) {
        return a ? 4 : b ? 2 : 1;
    }

    public int test(int a) {
        return foo(a == 3, true);
    }

    public int test2(int a, int b) {
        return foo(a == 3, b == 2);
    }

}
