package org.benf.cfr.tests;

import java.util.List;

public class TypeTest15 {

    private int getBoolFn(boolean b) {
        return b ? 1 : 0;
    }

    public int foo(List<Integer> l) {
        return l.get(0) + getBoolFn(false);

    }

}
