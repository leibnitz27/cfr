package org.benf.cfr.tests;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TypeTest19 {

    public void test3(List b) {
        List bcopy = new ArrayList<Boolean>(b);

        Iterator i = bcopy.iterator();
        while (i.hasNext()) {
            Boolean b2 = (Boolean) i.next();
            System.out.println(b);
        }
    }
}
