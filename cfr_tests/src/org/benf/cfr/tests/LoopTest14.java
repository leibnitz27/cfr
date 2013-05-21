package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class LoopTest14 {

    char[] foo;

    public void test(int end) {
        char ch;
        int x = 0;
        while ((ch = ((x % 2 == 0) ? foo[x + 1] : foo[x])) != '*') {
            System.out.println("" + x++ + ": " + ch);
        }
    }

    public boolean test1() {
        return true;
    }

    public void test2() {
        Object o = (Object) test1();
        System.out.println(o + " " + o.getClass());
    }

    public static void main(String args[]) {
        LoopTest14 l = new LoopTest14();
        l.test2();
    }

}
