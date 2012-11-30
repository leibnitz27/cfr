package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class EllipsisTest1 {

    void test1(String a, String b, String c) {
        test2(a, b, c);
        test2(new String[]{a, b, c});
        test3(new String[]{a, b, c});
    }

    void test2(String... a) {
        for (String x : a) {
            System.out.println(x);
        }
    }

    void test3(String[] a) {
        for (String x : a) {
            System.out.println(x);
        }
    }
}
