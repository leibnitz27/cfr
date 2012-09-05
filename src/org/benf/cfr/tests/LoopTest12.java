package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class LoopTest12 {


    public int test(int i, int j) {
        while (i < j)
            i = j++ / i;
        return 4;
    }


}
