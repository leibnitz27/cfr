package org.benf.cfr.reader.util;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/03/2013
 * Time: 05:45
 * <p/>
 * very simple enum to help switching on an XOR style decision.
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
