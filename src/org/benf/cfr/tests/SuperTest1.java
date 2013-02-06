package org.benf.cfr.tests;


public class SuperTest1 extends SuperBase {

    @Override
    public void test(SuperBase other) {
        super.test(other);
        other.test(this);
    }

}
