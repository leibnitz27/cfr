package org.benf.cfr.tests;

import java.util.ArrayList;
import java.util.List;

public class TypeTest12 {

    public void test3(List<Boolean> b) {
        List<Boolean> bcopy = new ArrayList<Boolean>(b);

        for (Boolean b2 : bcopy) {
            System.out.println(b);
        }
    }
}
