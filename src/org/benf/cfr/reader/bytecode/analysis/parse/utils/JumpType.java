package org.benf.cfr.reader.bytecode.analysis.parse.utils;

public enum JumpType {
    NONE("none", false),
    GOTO("goto", true),
    GOTO_OUT_OF_IF("goto_out_of_if", false),
    GOTO_OUT_OF_TRY("goto_out_of_try", false),
    BREAK("break", false),
    BREAK_ANONYMOUS("break anon", false),
    CONTINUE("continue", false),
    END_BLOCK("// endblock", false);


    private final String description;
    private final boolean isUnknown;

    JumpType(String description, boolean isUnknown) {
        this.description = description;
        this.isUnknown = isUnknown;
    }

    public boolean isUnknown() {
        return isUnknown;
    }

    @Override
    public String toString() {
        return description;
    }
}
