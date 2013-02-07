package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class ArrayTest9 {
    int x;
    int y;
    int z;

    static void test1(Integer... a) {
        System.out.println(a);
    }

    public static void main(String[] args) {
        test1(1, 2, 3);
        test1(1);
        test1(null);
        test1();
    }

}
