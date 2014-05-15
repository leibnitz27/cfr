package org.benf.cfr.reader.bytecode.analysis.parse.utils;

public enum BlockType {
    WHILELOOP(true),
    DOLOOP(true),
    UNCONDITIONALDOLOOP(true),
    FORLOOP(true),
    TRYBLOCK(false),
    SIMPLE_IF_TAKEN(false),
    SIMPLE_IF_ELSE(false),
    CATCHBLOCK(false),
    SWITCH(true),
    CASE(false),
    ANONYMOUS(true),
    MONITOR(false);

    private final boolean breakable;

    private BlockType(boolean breakable) {
        this.breakable = breakable;
    }

    public boolean isBreakable() {
        return breakable;
    }

}
