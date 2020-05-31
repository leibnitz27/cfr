package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.output.Dumper;

public class CommentStatement extends AbstractStatement {
    private final Expression text;

    public CommentStatement(String text) {
        this.text = new Literal(TypedLiteral.getString(text));
    }

    private CommentStatement(Expression expression) {
        this.text = expression;
    }

    public CommentStatement(Statement statement) {
        this.text = new StatementExpression(statement);
    }

    @Override
    public Statement deepClone(CloneHelper cloneHelper) {
        return new CommentStatement(cloneHelper.replaceOrClone(text));
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.dump(text);
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        text.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        text.applyExpressionRewriter(expressionRewriter, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        text.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredComment(text);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    /*
     * The idea of a statement expression is a bit weird, but
     */
    private static class StatementExpression extends AbstractExpression {
        private Statement statement;

        private static InferredJavaType javaType = new InferredJavaType(RawJavaType.VOID, InferredJavaType.Source.EXPRESSION);

        private StatementExpression(Statement statement) {
            super(javaType);
            this.statement = statement;
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }

        @Override
        public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
            statement.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers);
            return this;
        }

        @Override
        public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            statement.rewriteExpressions(expressionRewriter, ssaIdentifiers);
            return this;
        }

        @Override
        public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            return applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
        }

        @Override
        public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
            statement.collectLValueUsage(lValueUsageCollector);
        }

        @Override
        public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
            return false;
        }

        @Override
        public Expression deepClone(CloneHelper cloneHelper) {
            return this;
        }

        @Override
        public Precedence getPrecedence() {
            return Precedence.WEAKEST;
        }

        @Override
        public Dumper dumpInner(Dumper d) {
            return d.dump(statement);
        }
    }
}
