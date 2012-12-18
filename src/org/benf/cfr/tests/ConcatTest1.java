package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 03/04/2012
 */
public class ConcatTest1 {
    public void test1(String a, String b, int c) {
        String res = "This " + a + " is a " + b + " stringbuilder desugaring test " + c;
        System.out.println(res);

    }
}
