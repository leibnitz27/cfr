package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class AssignTest1 {
    int x;
    int y;
    int z;

    void test1(int a, int b) {
        int c = x;
        int e = a + b;
        x = e + c;
    }

    void test2(int a) {
        int b = 3;
        x = y = b = (a + 1) / 2;
    }

    void test3(int a) {
        x = ++a;
    }

    void test3b(int a) {
        x = a++;
    }

    void test4(int a, int b) {
        x = a < b ? a : b;
    }

    void test5(int a, int b) {
        if (a < b) {
            x = a;
        } else {
            x = b;
        }
    }


    void test6(int a, int b) {
        x = a < b ? a : ((a == 1) ? 3 : b);
    }
}
