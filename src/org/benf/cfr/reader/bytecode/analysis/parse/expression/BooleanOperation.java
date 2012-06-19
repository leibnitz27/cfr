package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 * Time: 06:45
 * To change this template use File | Settings | File Templates.
 */
public class BooleanOperation implements ConditionalExpression {
    private ConditionalExpression lhs;
    private ConditionalExpression rhs;
    private BoolOp op;

    public BooleanOperation(ConditionalExpression lhs, ConditionalExpression rhs, BoolOp op) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.op = op;
    }

    @Override
    public int getSize() {
        return 2 + lhs.getSize() + 2 + rhs.getSize();
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueCollector lValueCollector, SSAIdentifiers ssaIdentifiers) {
        return this;
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public String toString() {
        return "(" + lhs.toString() + ") " + op.getShowAs() + " (" + rhs.toString() + ")";
    }

    @Override
    public ConditionalExpression getNegated() {
        return new NotOperation(this);
    }

    @Override
    public ConditionalExpression getDemorganApplied(boolean amNegating) {
        return new BooleanOperation(lhs.getDemorganApplied(amNegating), rhs.getDemorganApplied(amNegating), amNegating ? op.getDemorgan() : op);
    }

    @Override
    public Set<LValue> getLoopLValues() {
        Set<LValue> res = SetFactory.newSet();
        res.addAll(lhs.getLoopLValues());
        res.addAll(rhs.getLoopLValues());
        return res;
    }
}
