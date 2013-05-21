package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class ArrayTest4 {
    int x;
    int y;
    int z;

    void test1(int a, int b) {
        String[] tmp = new String[]{"a", "b", "c"};
    }

    void test2(int a, int b) {
        int[] tmp = new int[]{a, b, x, y};
    }

    void test3(int a, int b) {
        int[][] tmp = new int[][]{{a, b}, {x, y}, {z}};
    }

    void test4(int a, int b) {
        int[][] tmp = new int[3][];
    }
}
