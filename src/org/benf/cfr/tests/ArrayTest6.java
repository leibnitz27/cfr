package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class ArrayTest6 {
    int x;
    int y;
    int z;

    void test3(int a, int b) {
        int[][] tmp = new int[3][3];
        tmp[1][2] = 4;
    }

    void test3a(int a, int b) {
        Integer[][] tmp = new Integer[3][3];
        tmp[1][2] = 4;
    }

    void test4() {
        int[] tmp2 = new int[4];
        tmp2[3] = 4;
    }
}
