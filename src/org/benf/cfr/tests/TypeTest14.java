package org.benf.cfr.tests;

public class TypeTest14 {

    public void test5(String fred) {
        int c = fred.charAt(5);
        if (c == 3297) {
            System.out.println("A");
        }
        System.out.print(c);
    }


    public void test6(String fred) {
        char c = fred.charAt(5);
        if (c == 3297) {
            System.out.println("A");
        }
        System.out.print(c);
    }


    public void test7(String fred) {
        char c = fred.charAt(5);
        if (c == 1) {
            System.out.println("A");
        }
        System.out.print(c);
    }

}
