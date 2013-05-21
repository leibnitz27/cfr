package org.benf.cfr.tests;


import java.util.List;

public class SuperTest2 extends SuperBase {

    private void jim() {
    }


    public void test(List<SuperTest2> others) {
        for (SuperTest2 other : others) other.jim();
        SuperTest2 other = others.get(0);
        other.jim();
        this.jim();

    }

}
