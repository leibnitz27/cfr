package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.KnownJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:03
 * To change this template use File | Settings | File Templates.
 */
public class ComparisonOperation extends AbstractExpression implements ConditionalExpression {
    private Expression lhs;
    private Expression rhs;
    private final CompOp op;

    public ComparisonOperation(Expression lhs, Expression rhs, CompOp op) {
        super(KnownJavaType.getKnownJavaType(JavaType.BOOLEAN));
        this.lhs = lhs;
        this.rhs = rhs;
        this.op = op;
    }

    @Override
    public int getSize() {
        return 3;
    }

    private String brace(Expression e) {
        if (e instanceof ComparisonOperation) return "(" + e + ")";
        return e.toString();
    }

    @Override
    public String toString() {
        return brace(lhs) + " " + op.getShowAs() + " " + brace(rhs);
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        lhs = lhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        rhs = rhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        if (lhs.canPushDownInto()) {
            if (rhs.canPushDownInto()) throw new ConfusedCFRException("2 sides of a comparison support pushdown?");
            Expression res = lhs.pushDown(rhs, this);
            if (res != null) return res;
        } else if (rhs.canPushDownInto()) {
            Expression res = rhs.pushDown(lhs, getNegated());
            if (res != null) return res;
        }
        return this;
    }

    @Override
    public ConditionalExpression getNegated() {
        return new ComparisonOperation(lhs, rhs, op.getInverted());
    }

    public CompOp getOp() {
        return op;
    }

    @Override
    public ConditionalExpression getDemorganApplied(boolean amNegating) {
        if (!amNegating) return this;
        return getNegated();
    }

    private void addIfLValue(Expression expression, Set<LValue> res) {
        if (expression instanceof LValueExpression) {
            res.add(((LValueExpression) expression).getLValue());
        }
    }

    @Override
    public Set<LValue> getLoopLValues() {
        Set<LValue> res = SetFactory.newSet();
        addIfLValue(lhs, res);
        addIfLValue(rhs, res);
        return res;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        lhs.collectUsedLValues(lValueUsageCollector);
        rhs.collectUsedLValues(lValueUsageCollector);
    }

}
