package org.benf.cfr.reader.bytecode.analysis.parse.expression.misc;

public enum Precedence {
    HIGHEST(true),
    PAREN_SUB_MEMBER(true),
    UNARY_POST(false),
    UNARY_OTHER(false),
    MUL_DIV_MOD(true),
    ADD_SUB(true),
    BITWISE_SHIFT(true),
    REL_CMP_INSTANCEOF(true),
    REL_EQ(true),
    BIT_AND(true),
    BIT_XOR(true),
    BIT_OR(true),
    LOG_AND(true, true),
    LOG_OR(true, true),
    CONDITIONAL(false),
    ASSIGNMENT(false),
    WEAKEST(true);

    private final boolean isLtoR;
    private final boolean commute;

    private Precedence(boolean ltoR) {
        this.isLtoR = ltoR;
        this.commute = false;
    }

    private Precedence(boolean ltoR, boolean commute) {
        this.isLtoR = ltoR;
        this.commute = commute;
    }

    public boolean isLtoR() {
        return isLtoR;
    }

    public boolean isCommutative() {
        return commute;
    }
}
