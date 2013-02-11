package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class DynamicCatchTest1 {
    int x;
    int y;
    int z;

    private static void rethrow(Throwable t) throws Throwable {
        throw t;
    }

    static void test1(int a, int b) {
        try {
            try {
                throw new UnsupportedOperationException();
            } catch (RuntimeException e) {
                rethrow(e);
            }
        } catch (UnsupportedOperationException e) {
            System.out.println("Dynamic");
        } catch (Throwable e) {
            System.out.println("Static");
        }
    }

    public static void main(String[] args) {
        test1(2, 3);
    }
}
