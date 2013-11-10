package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 * Time: 06:45
 * To change this template use File | Settings | File Templates.
 */
public class NotOperation extends AbstractExpression implements ConditionalExpression {
    private ConditionalExpression inner;

    public NotOperation(ConditionalExpression lhs) {
        super(lhs.getInferredJavaType());
        this.inner = lhs;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        inner.collectTypeUsages(collector);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new NotOperation((ConditionalExpression) cloneHelper.replaceOrClone(inner));
    }

    @Override
    public int getSize() {
        return 1 + inner.getSize();
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        inner = expressionRewriter.rewriteExpression(inner, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print("!(").dump(inner).print(")");
    }

    @Override
    public ConditionalExpression getNegated() {
        return inner;
    }

    @Override
    public ConditionalExpression getDemorganApplied(boolean amNegating) {
        return inner.getDemorganApplied(!amNegating);
    }

    @Override
    public Set<LValue> getLoopLValues() {
        return inner.getLoopLValues();
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        inner.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public ConditionalExpression optimiseForType() {
        return this;
    }

    @Override
    public ConditionalExpression simplify() {
        return ConditionalUtils.simplify(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;

        if (!(obj instanceof NotOperation)) return false;

        NotOperation other = (NotOperation) obj;
        return inner.equals(other.inner);
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        NotOperation other = (NotOperation) o;
        if (!constraint.equivalent(inner, other.inner)) return false;
        return true;
    }

}
