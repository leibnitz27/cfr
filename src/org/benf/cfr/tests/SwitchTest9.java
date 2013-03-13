package org.benf.cfr.tests;

import org.benf.cfr.reader.util.Troolean;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 18/12/2012
 * Time: 07:22
 * <p/>
 * Test that default is lifted inside the switch
 */
public class SwitchTest9 {

    public void test1(int a, int b) {
        switch (Troolean.get(a == 3, b == 12)) {
            case NEITHER:
                System.out.println("0");
            case FIRST:
                System.out.println("1");
                break;
            case BOTH:
                System.out.println("2");
            default:
        }
        System.out.println("Test");
    }

}
