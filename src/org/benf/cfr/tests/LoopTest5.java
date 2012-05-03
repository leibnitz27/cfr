package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class LoopTest5 {


    public void test5(int end) {
        for (int x = 0; (x = x + 2) < end; ) {
            System.out.println(x);
        }
    }


}
