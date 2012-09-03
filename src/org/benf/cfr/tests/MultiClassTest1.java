package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class MultiClassTest1 {
    int x;
    int y;
    int z;

    int test1(int a, int b) {
        return a + b;
    }
}

/*
 * Is there any way to differentiate between this and a class declared elsewhere?
 */
class ExtraClass {
    int foo;

    public int testfoo(int x, int y) {
        return x + y * 2;
    }

    public int testbar(int a) {
        return a + 2;
    }
}
