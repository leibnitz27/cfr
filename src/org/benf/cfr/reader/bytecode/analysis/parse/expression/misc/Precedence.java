package org.benf.cfr.reader.bytecode.analysis.parse.expression.misc;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/02/2014
 * Time: 06:11
 */
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
    LOG_AND(true),
    LOG_OR(true),
    CONDITIONAL(false),
    ASSIGNMENT(false),
    WEAKEST(true);

    private final boolean isLtoR;

    private Precedence(boolean ltoR) {
        isLtoR = ltoR;
    }

    public boolean isLtoR() {
        return isLtoR;
    }
}
