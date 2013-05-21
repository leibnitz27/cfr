package org.benf.cfr.tests.thirdparty;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 15:12
 */
public class Casting {
    public static void main(String args[]) {
        for (char c = 0; c < 128; c++) {
            System.out.println("ascii " + (int) c + " character " + c);
        }
    }
}