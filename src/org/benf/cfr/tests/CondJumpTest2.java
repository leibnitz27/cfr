package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 27/05/2011
 * Time: 06:54
 * To change this template use File | Settings | File Templates.
 */
public class CondJumpTest2 {


    public boolean test(boolean  a, boolean  b)
    {
        boolean  c;
        if (b && a == (c = b) && b && c)
        {
            return true;
        }
        return false;
    }
}
