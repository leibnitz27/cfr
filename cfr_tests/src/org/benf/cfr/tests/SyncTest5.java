package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class SyncTest5 {

    private int x;

    public void test1() {
        if (x == 4) {
            synchronized (this) {
                x = 3;
            }
        }
    }
}
