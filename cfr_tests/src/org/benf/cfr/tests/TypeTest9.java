package org.benf.cfr.tests;

import java.util.List;

public class TypeTest9 {

    public void test2(boolean[] b) {
        if (b.length == 0) {
            System.out.println(b);
        }
    }


    public void test3(List<Boolean> b) {
        if (b.isEmpty()) {
            System.out.println(b);
        }
    }
}
