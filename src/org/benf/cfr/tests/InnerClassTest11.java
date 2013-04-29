package org.benf.cfr.tests;

import org.benf.cfr.reader.util.SetFactory;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/09/2012
 * Time: 07:09
 */
public class InnerClassTest11 {
    private int a;

    public void foo() {
        Inner1 i = new Inner1(5);
        System.out.println(i.x);
    }

    public class Inner1 {
        private int x;

        public Inner1(int x) {
            a += x;
            this.x = x + 1 + a;
        }

        public InnerClassTest4.InnerBase tweak(int y) {
            return new InnerClassTest4.InnerBase(SetFactory.newSet(y));
        }


    }
}
