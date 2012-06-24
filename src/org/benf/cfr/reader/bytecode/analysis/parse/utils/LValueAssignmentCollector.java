package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 20/03/2012
 * Time: 18:06
 * To change this template use File | Settings | File Templates.
 */
public class LValueAssignmentCollector implements LValueRewriter {

    private final Map<LValue, Pair<Expression, StatementContainer>> found = MapFactory.newMap();

    public void collect(LValue lValue, StatementContainer statementContainer, Expression value) {
        found.put(lValue, new Pair<Expression, StatementContainer>(value, statementContainer));
    }

    @Override
    public Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers) {
        if (!found.containsKey(lValue)) return null;
        Pair<Expression, StatementContainer> pair = found.get(lValue);
        // res is a valid replacement for lValue in an rValue, IF no mutable fields have different version
        // identifiers (SSA tags)
        StatementContainer statementContainer = pair.getSecond();
        SSAIdentifiers replacementIdentifiers = statementContainer.getSSAIdentifiers();
        // We're saying we can replace lValue with res.
        // This is only valid if res has a single possible value in ssaIdentifiers, and it's the same as in replacementIdentifiers.
        Expression res = pair.getFirst();
        Expression prev = null;
        if (res instanceof LValueExpression) {
            LValue resLValue = ((LValueExpression) res).getLValue();
            if (!ssaIdentifiers.isValidReplacement(resLValue, replacementIdentifiers)) return null;
        }
        pair.getSecond().nopOut();
        do {
            prev = res;
            res = res.replaceSingleUsageLValues(this, ssaIdentifiers);
//            System.out.println("can replace " + prev + " with " + res);
        } while (res != null && res != prev);
        return prev;
    }
}
