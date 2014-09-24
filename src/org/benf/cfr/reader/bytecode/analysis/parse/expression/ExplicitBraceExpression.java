package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.output.Dumper;

public class ExplicitBraceExpression extends AbstractExpression {
    private Expression contained;

    public ExplicitBraceExpression(Expression contained) {
        // Type won't change over monop (??)
        super(contained.getInferredJavaType());
        this.contained = contained;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        contained.collectTypeUsages(collector);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ExplicitBraceExpression(cloneHelper.replaceOrClone(contained));
    }

    @Override
    public Precedence getPrecedence() {
        return contained.getPrecedence();
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        d.print("(");
        contained.dumpWithOuterPrecedence(d, contained.getPrecedence(), Troolean.NEITHER);
        d.print(")");
        return d;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        contained = contained.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        contained = expressionRewriter.rewriteExpression(contained, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        contained.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExplicitBraceExpression that = (ExplicitBraceExpression) o;

        if (contained != null ? !contained.equals(that.contained) : that.contained != null) return false;
        return true;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExplicitBraceExpression other = (ExplicitBraceExpression) o;
        if (!constraint.equivalent(contained, other.contained)) return false;
        return true;
    }
}
