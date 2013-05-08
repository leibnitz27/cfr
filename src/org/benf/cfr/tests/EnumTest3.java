package org.benf.cfr.tests;

import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 22/08/2012
 * Time: 20:58
 */
public enum EnumTest3 {
    FOO(2),
    BAR(1),
    BAP(5);

    private final int x;
    private final static Map<String, EnumTest3> smap = MapFactory.newMap();
    private final static EnumTest3 tmp = BAP;

    private EnumTest3(int x) {
        this.x = x;
    }

    public static void main(String args[]) {
        System.out.println(FOO);
    }
}
