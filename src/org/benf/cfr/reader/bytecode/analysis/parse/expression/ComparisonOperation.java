package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 */
public class ComparisonOperation extends AbstractExpression implements ConditionalExpression, BoxingProcessor {
    private Expression lhs;
    private Expression rhs;
    private final CompOp op;

    public ComparisonOperation(Expression lhs, Expression rhs, CompOp op) {
        super(new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.EXPRESSION));
        this.lhs = lhs;
        this.rhs = rhs;
        /*
         * RE: Integral types.
         *
         * If we're comparing two non literals, we can't really tell anything.  In fact, we should
         * use the widest type (if appropriate).
         *
         * If one is a literal, we can see if that literal supports conversion to the type we've guessed
         * for the lhs.  If so, we can tighten that literal.
         *
         * i.e.
         *
         * int x = ..
         * if (x == 97)
         *
         * vs
         *
         * char x = .. (creates a KNOWN char type, i.e. string.charAt)
         * if (x == 'a') .... comparison in both types will be the same...
         *
         * but if we've incorrectly guessed the literal as a narrower type before (eg boolean), we need to
         * bring it back up to the other type.
         */
        InferredJavaType.compareAsWithoutCasting(lhs.getInferredJavaType(), rhs.getInferredJavaType());
        this.op = op;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ComparisonOperation(cloneHelper.replaceOrClone(lhs), cloneHelper.replaceOrClone(rhs), op);
    }

    @Override
    public int getSize() {
        return 3;
    }

    private Dumper brace(Expression e, Dumper d) {
        if (e instanceof ComparisonOperation) return d.print("(").dump(e).print(")");
        return e.dump(d);
    }

    @Override
    public Dumper dump(Dumper d) {
        brace(lhs, d);
        d.print(" " + op.getShowAs() + " ");
        brace(rhs, d);
        return d;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        lhs = lhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        rhs = rhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        /*
         * TODO: This should be rewritten in terms of an expressionRewriter.
         */
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
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        lhs = expressionRewriter.rewriteExpression(lhs, ssaIdentifiers, statementContainer, flags);
        rhs = expressionRewriter.rewriteExpression(rhs, ssaIdentifiers, statementContainer, flags);
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
        if (a.getInferredJavaType().getJavaTypeInstance().getRawTypeOfSimpleType() != RawJavaType.BOOLEAN)
            return BooleanComparisonType.NOT;
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
    public ConditionalExpression simplify() {
        return ConditionalUtils.simplify(this);
    }

    @Override
    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        switch (op) {
            case EQ:
            case NE:
                if (boxingRewriter.isUnboxedType(lhs)) {
                    rhs = boxingRewriter.sugarUnboxing(rhs);
                    return false;
                }
                if (boxingRewriter.isUnboxedType(rhs)) {
                    lhs = boxingRewriter.sugarUnboxing(lhs);
                    return false;
                }
                break;
            default:
                lhs = boxingRewriter.sugarUnboxing(lhs);
                rhs = boxingRewriter.sugarUnboxing(rhs);
                break;
        }
        return false;
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

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        ComparisonOperation other = (ComparisonOperation) o;
        if (!constraint.equivalent(op, other.op)) return false;
        if (!constraint.equivalent(lhs, other.lhs)) return false;
        if (!constraint.equivalent(rhs, other.rhs)) return false;
        return true;
    }

}
