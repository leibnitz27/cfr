package org.benf.cfr.tests;

import org.omg.CORBA.StringHolder;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class ThisConstructorTest {

    private final int x;
    private final String y;

    public ThisConstructorTest(int x, String y) {
        this.x = x;
        this.y = y;
    }

    public ThisConstructorTest(int x) {
        this(x, "Null");
    }

    public ThisConstructorTest() {
        this(3);
    }
}
