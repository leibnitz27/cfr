package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class LoopTest17 {

    char[] foo;

    public void test(int end) {
        int x = 3;
        do {
            System.out.println(x++);
            if (x > 5) break;
            System.out.println("fred");
        } while (true);
    }

}
