package org.benf.cfr.tests;

import java.util.ArrayList;
import java.util.List;

public class TypeTest13 {

    public void test1(String fred) {
        char c = fred.charAt(5);
        if (c == 'a') {
            System.out.println("A");
        }
        System.out.print(c);
    }

    public void test2(String fred) {
        int c = fred.charAt(5);
        if (c == 'a') {
            System.out.println("A");
        }
        System.out.print(c);
    }

    public void test3(String fred) {
        char c = fred.charAt(5);
        if (c == 'a') {
            System.out.println("A");
        }
        System.out.print((int) c);
    }

    public void test4(String fred) {
        int c = fred.charAt(5);
        if (c == 97) {
            System.out.println("A");
        }
        System.out.print(c);
    }

    public void test5(String fred) {
        char c = fred.charAt(5);
        if (c == 3297) {
            System.out.println("A");
        }
        System.out.print(c);
    }
}
