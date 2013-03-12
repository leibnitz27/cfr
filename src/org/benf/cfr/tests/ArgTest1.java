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
public class ArgTest1 {

    private int test(boolean a, boolean b) {
        return a ? 4 : b ? 2 : 1;
    }

    public int test2(int a, int b) {
        return test(a == 3, b == 4);
    }

}
