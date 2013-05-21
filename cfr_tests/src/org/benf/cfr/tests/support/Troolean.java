package org.benf.cfr.tests.support;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/05/2013
 * Time: 17:06
 */
public enum Troolean {
    NEITHER,
    FIRST,
    SECOND,
    BOTH;

    public static Troolean get(boolean a, boolean b) {
        if (a) {
            if (b) return BOTH;
            return FIRST;
        }
        if (b) return SECOND;
        return NEITHER;
    }
}
