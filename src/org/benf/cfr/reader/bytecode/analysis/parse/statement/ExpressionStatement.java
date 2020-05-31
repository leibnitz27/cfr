package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.output.Dumper;

public class ExpressionStatement extends AbstractStatement {
    private Expression expression;

    public ExpressionStatement(Expression expression) {
        this.expression = expression;
    }

    @Override
    public Dumper dump(Dumper d) {
        return expression.dump(d).endCodeln();
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        expression = expression.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        expression = expressionRewriter.rewriteExpression(expression, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public Statement deepClone(CloneHelper cloneHelper) {
        return new ExpressionStatement(cloneHelper.replaceOrClone(expression));
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        expression.collectUsedLValues(lValueUsageCollector);
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredExpressionStatement(expression, false);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof ExpressionStatement)) return false;
        ExpressionStatement other = (ExpressionStatement) o;
        return expression.equals(other.expression);
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return expression.canThrow(caught);
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof ExpressionStatement)) return false;
        ExpressionStatement other = (ExpressionStatement) o;
        return constraint.equivalent(expression, other.expression);
    }
}
