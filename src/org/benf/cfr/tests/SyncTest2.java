package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class SyncTest2 {

    private int x;

    public void test1() {
        synchronized (this) {
            try {
                x = 3;
            } catch (RuntimeException e) {
                x = 4;
            } finally {
                x = 5;
            }
        }
    }
}
