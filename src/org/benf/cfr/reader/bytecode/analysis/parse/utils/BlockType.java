package org.benf.cfr.reader.bytecode.analysis.parse.utils;

public enum BlockType {
    WHILELOOP(true, true),
    DOLOOP(true, true),
    UNCONDITIONALDOLOOP(true, true),
    FORLOOP(true, true),
    TRYBLOCK(false, false),
    SIMPLE_IF_TAKEN(false, false),
    SIMPLE_IF_ELSE(false, false),
    CATCHBLOCK(false, false),
    SWITCH(true, false),
    CASE(false, false),
    ANONYMOUS(true, false),
    MONITOR(false, false);

    private final boolean breakable;
    private final boolean isloop;

    private BlockType(boolean breakable, boolean isloop) {
        this.breakable = breakable;
        this.isloop = isloop;
    }

    public boolean isBreakable() {
        return breakable;
    }

    public boolean isLoop() { return isloop; }

}
