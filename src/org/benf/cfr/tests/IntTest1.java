package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 18:05
 * To change this template use File | Settings | File Templates.
 */
public class IntTest1 {

    public int plus1(int x)
    {
        return x+1;
    }

    public int func1(int x, int y)
    {
        return x+((y-1)*(x+1));
    }

    public int func2(int x, int y)
    {
        return x+(x > y ? x+1 : y-1);
    }
}
