package org.benf.cfr.tests;

import java.util.List;

public class TypeTest11 {

    public void test2(boolean a) {
    }


    public void test3(List<Boolean> b) {
        boolean x = true;
        if (!x) {
            test2(x);
        }
    }
}
