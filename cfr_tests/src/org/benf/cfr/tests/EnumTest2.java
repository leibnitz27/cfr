package org.benf.cfr.tests;


import org.benf.cfr.tests.support.MapFactory;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 22/08/2012
 * Time: 20:58
 */
public enum EnumTest2 {
    FOO(2),
    BAR(1),
    BAP(5);

    private final int x;
    private final static Map<String, EnumTest2> smap = MapFactory.newMap();

    private EnumTest2(int x) {
        this.x = x;
    }
}
