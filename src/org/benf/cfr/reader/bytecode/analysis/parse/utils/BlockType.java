package org.benf.cfr.reader.bytecode.analysis.parse.utils;

/**
 * Created:
 * User: lee
 * Date: 01/05/2012
 */
public enum BlockType {
    WHILELOOP(true),
    DOLOOP(true),
    FORLOOP(true),
    TRYBLOCK(false),
    SIMPLE_IF_TAKEN(false),
    SIMPLE_IF_ELSE(false),
    CATCHBLOCK(false),
    SWITCH(true),
    CASE(false),
    MONITOR(false);

    private final boolean breakable;

    private BlockType(boolean breakable) {
        this.breakable = breakable;
    }

    public boolean isBreakable() {
        return breakable;
    }

}
