package org.benf.cfr.tests;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/09/2012
 * Time: 07:09
 */
public class InnerClassTest0_3 {

    private final int x;

    public InnerClassTest0_3(int y) {
        this.x = y;
        new Inner1();
    }

    public class Inner1 {
        public Inner1() {
            System.out.println(x);
        }
    }
}
