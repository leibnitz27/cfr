package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 */
public class ComparisonOperation extends AbstractExpression implements ConditionalExpression {
    private Expression lhs;
    private Expression rhs;
    private final CompOp op;

    public ComparisonOperation(Expression lhs, Expression rhs, CompOp op) {
        super(new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.EXPRESSION));
        this.lhs = lhs;
        this.rhs = rhs;
        InferredJavaType.compareAsWithoutCasting(lhs.getInferredJavaType(), rhs.getInferredJavaType());
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


    protected void addIfLValue(Expression expression, Set<LValue> res) {
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

    private enum BooleanComparisonType {
        NOT(false),
        AS_IS(true),
        NEGATED(true);

        private final boolean isValid;

        private BooleanComparisonType(boolean isValid) {
            this.isValid = isValid;
        }

        public boolean isValid() {
            return isValid;
        }
    }

    private static BooleanComparisonType isBooleanComparison(Expression a, Expression b, CompOp op) {
        switch (op) {
            case EQ:
            case NE:
                break;
            default:
                return BooleanComparisonType.NOT;
        }
        if (!(b instanceof Literal)) return BooleanComparisonType.NOT;
        Literal literal = (Literal) b;
        TypedLiteral lit = literal.getValue();
        if (lit.getType() != TypedLiteral.LiteralType.Integer) return BooleanComparisonType.NOT;
        int i = (Integer) lit.getValue();
        if (i < 0 || i > 1) return BooleanComparisonType.NOT;
        if (op == CompOp.NE) i = 1 - i;
        // Can now consider op to be EQ
        if (i == 0) {
            return BooleanComparisonType.NEGATED;
        } else {
            return BooleanComparisonType.AS_IS;
        }
    }

    public ConditionalExpression getConditionalExpression(Expression booleanExpression, BooleanComparisonType booleanComparisonType) {
        ConditionalExpression res = null;
        if (booleanExpression instanceof ConditionalExpression) {
            res = (ConditionalExpression) booleanExpression;
        } else {
            res = new BooleanExpression(booleanExpression);
        }
        if (booleanComparisonType == BooleanComparisonType.NEGATED) res = res.getNegated();
        return res;
    }

    @Override
    public ConditionalExpression optimiseForType() {
        BooleanComparisonType bct = null;
        if ((bct = isBooleanComparison(lhs, rhs, op)).isValid()) {
            return getConditionalExpression(lhs, bct);
        } else if ((bct = isBooleanComparison(rhs, lhs, op)).isValid()) {
            return getConditionalExpression(rhs, bct);
        }
        return this;
    }

    public Expression getLhs() {
        return lhs;
    }

    public Expression getRhs() {
        return rhs;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ComparisonOperation)) return false;
        ComparisonOperation other = (ComparisonOperation) o;
        return op == other.op &&
                lhs.equals(other.lhs) &&
                rhs.equals(other.rhs);
    }
}
