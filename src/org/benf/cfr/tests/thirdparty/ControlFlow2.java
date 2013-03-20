package org.benf.cfr.tests.thirdparty;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 15:14
 */
public class ControlFlow2 {
    public int foo(int i, int j) {
        while (true) {
            try {
                while (i < j)
                    i = j++ / i;
            } catch (RuntimeException re) {
                i = 10;
                continue;
            }
            System.out.println("Here.");
            if (i < 4) break;
        }
        return j;
    }
}
