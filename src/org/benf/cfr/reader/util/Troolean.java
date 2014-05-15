package org.benf.cfr.reader.util;

/**
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
