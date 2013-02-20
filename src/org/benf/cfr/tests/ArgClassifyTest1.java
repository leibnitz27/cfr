package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class ArgClassifyTest1 {

    void test1(String a) {
        System.out.println("a");
    }

    void test1(Object a) {
        System.out.println("b");
    }

    public static void main(String[] args) {
        String a = "fdfd";
        ArgClassifyTest1 ar = new ArgClassifyTest1();
        ar.test1(a);
        Object b = a;
        ar.test1(b);
    }
}
