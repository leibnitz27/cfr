package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;

public class ConditionalUtils {
    public static ConditionalExpression simplify(ConditionalExpression condition) {
        ConditionalExpression applyDemorgan = condition.getDemorganApplied(false);
        if (applyDemorgan.getSize() < condition.getSize()) {
            condition = applyDemorgan;
        }
        return condition;
    }
}
