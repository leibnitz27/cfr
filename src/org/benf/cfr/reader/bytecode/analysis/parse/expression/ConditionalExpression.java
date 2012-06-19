package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:02
 * To change this template use File | Settings | File Templates.
 */
public interface ConditionalExpression extends Expression {
    ConditionalExpression getNegated();

    int getSize();

    ConditionalExpression getDemorganApplied(boolean amNegating);

    Set<LValue> getLoopLValues();
}
