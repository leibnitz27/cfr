package org.benf.cfr.tests;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/09/2012
 * Time: 07:09
 */
public class InnerClassTest0_5 {

    private final Integer x = 3;
    private int y;

    public void foo() {
        new Inner1();
    }

    public class Inner1 {
        public Inner1() {
            System.out.println(x);
            System.out.println(y);
        }

        public void foo() {
            System.out.println(x);
            System.out.println(y);
            System.out.println(Math.max(x, y));
        }
    }
}
