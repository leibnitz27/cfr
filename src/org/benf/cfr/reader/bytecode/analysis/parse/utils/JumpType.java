package org.benf.cfr.reader.bytecode.analysis.parse.utils;

/**
 * Created:
 * User: lee
 * Date: 02/05/2012
 */
public enum JumpType {
    GOTO("goto"),
    BREAK("break [ wrong atm, shows end ]"),
    CONTINUE("continue");

    private final String description;

    private JumpType(String description) {
        this.description = description;
    }


    @Override
    public String toString() {
        return description;
    }
}
