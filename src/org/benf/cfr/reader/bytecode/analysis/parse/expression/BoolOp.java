package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 20/03/2012
 * Time: 06:34
 * To change this template use File | Settings | File Templates.
 */
public enum BoolOp {
    OR("||", Precedence.LOG_OR),
    AND("&&", Precedence.LOG_AND);

    private final String showAs;
    private final Precedence precedence;

    private BoolOp(String showAs, Precedence precedence) {
        this.showAs = showAs;
        this.precedence = precedence;
    }

    public String getShowAs() {
        return showAs;
    }

    public Precedence getPrecedence() {
        return precedence;
    }

    public BoolOp getDemorgan() {
        switch (this) {
            case OR:
                return AND;
            case AND:
                return OR;
            default:
                throw new ConfusedCFRException("Unknown op.");
        }
    }
}
