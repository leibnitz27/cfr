package org.benf.cfr.tests;

import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

public class AssertTest2 {

    final static Map<String, String> map = MapFactory.newMap();
    final static Map<String, String> map2;

    static {
        map2 = MapFactory.newMap();
    }

    public void test1(String s) {
        assert (!s.equals("Fred"));
        System.out.println(s);
    }
}
