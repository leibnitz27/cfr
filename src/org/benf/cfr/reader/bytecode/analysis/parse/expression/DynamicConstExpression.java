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
import org.benf.cfr.reader.util.output.Dumper;

/*
 * A dynamic constant expression isn't currently emitted by javac, so the best we can do is produce
 * something that *LOOKS* like it, and is legible.
 */
public class DynamicConstExpression extends AbstractExpression {
    private Expression content;

    public DynamicConstExpression(Expression content) {
        super(content.getInferredJavaType());
        this.content = content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (!(o instanceof DynamicConstExpression)) return false;
        return content.equals(((DynamicConstExpression) o).content);
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.WEAKEST;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        d.print(" /* dynamic constant */ ").separator("(").dump(content.getInferredJavaType().getJavaTypeInstance()).separator(")").dump(content);
        return d;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        content = content.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression newContent = content.applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
        if (newContent == content) return this;
        return new DynamicConstExpression(newContent);
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression newContent = content.applyReverseExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
        if (newContent == content) return this;
        return new DynamicConstExpression(newContent);
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        content.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof  DynamicConstExpression)) return false;
        return content.equivalentUnder(((DynamicConstExpression) o).content, constraint);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new DynamicConstExpression(content.deepClone(cloneHelper));
    }
}
