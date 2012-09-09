package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.MatchableBoolean;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:44
 * To change this template use File | Settings | File Templates.
 */
public class PostAdjustExpression extends AbstractExpression {
    private final LValue lValue;
    private final MatchableBoolean isPlus;
    private final Expression amount;

    public PostAdjustExpression(LValue lValue, boolean isPlus, Expression amount) {
        super(lValue.getInferredJavaType());
        this.lValue = lValue;
        this.amount = amount;
        this.isPlus = MatchableBoolean.get(isPlus);
    }


    @Override
    public String toString() {
        return "(" + lValue + " " + (isPlus.getValue() ? "+" : "-") + "= " + amount + ")";
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        lValueUsageCollector.collect(lValue);
        amount.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof PostAdjustExpression)) return false;
        PostAdjustExpression other = (PostAdjustExpression) o;
        return lValue.equals(other.lValue) &&
                isPlus.equals(other.isPlus) &&
                amount.equals(other.amount);
    }
}
