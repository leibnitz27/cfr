package org.benf.cfr.reader.bytecode.analysis.parse.utils;

/**
 * Created:
 * User: lee
 * Date: 02/05/2012
 */
public enum JumpType {
    NONE("none", false),
    GOTO("goto", true),
    GOTO_OUT_OF_IF("goto_out_of_if", false),
    GOTO_OUT_OF_TRY("goto_out_of_try", false),
    BREAK("break", false),
    CONTINUE("continue", false);

    private final String description;
    private final boolean isUnknown;

    private JumpType(String description, boolean isUnknown) {
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
