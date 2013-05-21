package org.benf.cfr.tests;

import java.util.List;

public class TypeTest10 {

    public void test2(int a) {
    }


    public void test3(List<Boolean> b) {
        int x = 1;
        if (x == 0) {
            test2(x);
        }
    }
}
