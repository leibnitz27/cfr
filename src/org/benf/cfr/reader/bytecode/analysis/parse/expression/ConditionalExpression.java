package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;

import java.util.Set;

public interface ConditionalExpression extends Expression {
    ConditionalExpression getNegated();

    int getSize();

    ConditionalExpression getDemorganApplied(boolean amNegating);

    Set<LValue> getLoopLValues();

    ConditionalExpression optimiseForType();

    ConditionalExpression simplify();
}
