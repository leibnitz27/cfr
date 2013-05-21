package org.benf.cfr.tests;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/09/2012
 * Time: 07:09
 */
public class InnerClassTest9 {

    public int x = 3;

    public class Inner1 {

        public int tweakX(int y) {
            x += y;
            return x;
        }

    }
}
