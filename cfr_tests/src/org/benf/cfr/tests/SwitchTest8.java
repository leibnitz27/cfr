package org.benf.cfr.tests;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 18/12/2012
 * Time: 07:22
 * <p/>
 * Test that default is lifted inside the switch
 */
public class SwitchTest8 {
    private int foo(boolean a, boolean b) {
        return a ? 1 : b ? 2 : 3;
    }

    public void test1(int a, int b) {
        switch (foo(a == 3, b == 12)) {
            case 0:
                System.out.println("0");
            case 1:
                System.out.println("1");
                break;
            case 2:
                System.out.println("2");
            default:
        }
        System.out.println("Test");
    }

}
