package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;

import java.util.Set;

public interface ConditionalExpression extends Expression {
    ConditionalExpression getNegated();

    int getSize();

    ConditionalExpression getDemorganApplied(boolean amNegating);

    /*
     * Normalise tree layout so ((a || b) || c) --> (a || (b || c)).
     * This is useful so any patterns can know what they're matching against.
     */
    ConditionalExpression getRightDeep();

    Set<LValue> getLoopLValues();

    ConditionalExpression optimiseForType();

    ConditionalExpression simplify();
}
