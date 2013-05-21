package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class TryTest1 {


    public void test1() {
        try {
            try {
                System.out.print(3);
                throw new NoSuchFieldException();
            } catch (NoSuchFieldException e) {
            }
        } finally {
            System.out.print("Finally!");
        }
        System.out.print(5);
    }
}
