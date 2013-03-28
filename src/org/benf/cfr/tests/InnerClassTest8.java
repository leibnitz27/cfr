package org.benf.cfr.tests;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/09/2012
 * Time: 07:09
 */
public class InnerClassTest8 {

    private int x = 3;

    public class Inner1 {

        public int getX(int y) {
            x += y;
            return x;
        }

    }
}
