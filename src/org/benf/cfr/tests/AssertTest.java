package org.benf.cfr.tests;

import java.util.List;

public class AssertTest {

    public void test1(String s) {
        assert (!s.equals("Fred"));
        System.out.println(s);
    }
}
