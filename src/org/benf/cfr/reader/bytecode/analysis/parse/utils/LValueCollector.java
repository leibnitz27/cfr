package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 20/03/2012
 * Time: 18:06
 * To change this template use File | Settings | File Templates.
 */
public class LValueCollector {

    private final Map<LValue, Pair<Expression, StatementContainer>> found = MapFactory.newMap();

    public void collect(LValue lValue, StatementContainer statementContainer, Expression value) {
        found.put(lValue, new Pair<Expression, StatementContainer>(value, statementContainer));
    }

    public Expression getLValueReplacement(LValue lValue) {
        if (!found.containsKey(lValue)) return null;
        Pair<Expression, StatementContainer> pair = found.get(lValue);
        // res is a valid replacement for lValue in an rValue, IF no mutable fields have different version
        // identifiers (SSA tags)
        Expression res = pair.getFirst();
        Expression prev = null;
        pair.getSecond().nopOut();
        do {
            prev = res;
            res = res.replaceSingleUsageLValues(this);
//            System.out.println("can replace " + prev + " with " + res);
        } while (res != null && res != prev);
        return prev;
    }
}
