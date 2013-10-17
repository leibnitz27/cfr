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
    TRUE,
    FALSE;

    public static Troolean get(Boolean a) {
        if (a == null) return NEITHER;
        return a ? TRUE : FALSE;
    }
}
