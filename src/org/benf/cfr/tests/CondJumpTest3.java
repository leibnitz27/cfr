package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 07/06/2011
 * Time: 06:24
 * To change this template use File | Settings | File Templates.
 */
public class CondJumpTest3 {

    public boolean test(boolean  a, boolean  b)
    {
        boolean  c;
        return (b && a == (c = b) && b && c);
    }
}
