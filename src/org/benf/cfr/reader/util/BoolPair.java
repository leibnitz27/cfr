package org.benf.cfr.reader.util;

/**
 * very simple enum to help switching on an XOR style decision.
 */
public enum BoolPair {
    NEITHER(0),
    FIRST(1),
    SECOND(1),
    BOTH(2);

    private final int count;

    BoolPair(int count) {
        this.count = count;
    }

    public static BoolPair get(boolean a, boolean b) {
        if (a) {
            if (b) return BOTH;
            return FIRST;
        }
        if (b) return SECOND;
        return NEITHER;
    }

    public int getCount() {
        return count;
    }
}
