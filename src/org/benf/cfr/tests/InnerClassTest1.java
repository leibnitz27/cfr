package org.benf.cfr.tests;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/09/2012
 * Time: 07:09
 */
public class InnerClassTest1 {

    public int getX() {
        return 3;
    }

    public class Inner1 {

        public int getX() {
            return 2;
        }

        @Override
        public String toString() {
            return "" + getX() + InnerClassTest1.this.getX();
        }
    }
}
