package org.benf.cfr.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/09/2012
 * Time: 07:09
 */
public class InnerClassTest5 {

    public int getX() {
        return new Inner1(new ArrayList<String>()).getX(4);
    }

    public static class Inner1 {
        private final List arg;

        public Inner1(List arg) {
            this.arg = arg;
        }

        public int getX(int y) {
            return 2;
        }

    }
}
