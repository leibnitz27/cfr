package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 18/06/2012
 * Time: 07:24
 * To change this template use File | Settings | File Templates.
 */
public class ConditionalUtils {
    public static ConditionalExpression simplify(ConditionalExpression condition) {
        ConditionalExpression applyDemorgan = condition.getDemorganApplied(false);
        if (applyDemorgan.getSize() < condition.getSize()) {
            condition = applyDemorgan;
        }
        return condition;
    }
}
