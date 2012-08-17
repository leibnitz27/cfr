package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class SyncTest3 {

    private int x = 1;
    private Object y;

    public void test1() {
        synchronized (this) {
            try {
                if (x == 4) return;
                synchronized (y) {
                    x = 5;
                }
                x = 3;
                return;
            } catch (RuntimeException e) {
                x = 4;
            }
        }
    }
}
