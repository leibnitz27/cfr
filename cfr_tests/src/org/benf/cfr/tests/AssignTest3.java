package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class AssignTest3 {
    int x;
    int y;
    int z;

    void test3(int a) {
        x = ++a;
    }

    void test3b(int a) {
        x = a++;
    }
}
