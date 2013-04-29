package org.benf.cfr.tests;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/09/2012
 * Time: 07:09
 */
public class InnerClassTest0_4 {

    private Integer x = 3;

    public void foo() {
        new Inner1();
    }

    public class Inner1 {
        public Inner1() {
            System.out.println(x);
        }
    }
}
