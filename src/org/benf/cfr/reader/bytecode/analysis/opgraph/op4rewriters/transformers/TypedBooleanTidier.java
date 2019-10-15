package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;

public class TypedBooleanTidier implements StructuredStatementTransformer, ExpressionRewriter {

    public void transform(Op04StructuredStatement root) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        in.transformStructuredChildren(this, scope);
        in.rewriteExpressions(this);
        return in;
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof ConditionalExpression) {
            return rewriteExpression((ConditionalExpression) expression, ssaIdentifiers, statementContainer, flags);
        }
        return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression = (ConditionalExpression) expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        if (!(expression instanceof ComparisonOperation)) return expression;

        ComparisonOperation comparisonOperation = (ComparisonOperation) expression;
        Expression lhs = comparisonOperation.getLhs();
        Expression rhs = comparisonOperation.getRhs();
        CompOp op = comparisonOperation.getOp();
        if (!(lhs.getInferredJavaType().getJavaTypeInstance() == RawJavaType.BOOLEAN &&
                rhs.getInferredJavaType().getJavaTypeInstance() == RawJavaType.BOOLEAN &&
                (op == CompOp.EQ || op == CompOp.NE))) return expression;

        if (!(rhs instanceof Literal)) return expression;

        boolean b = ((Literal) rhs).getValue().getBoolValue();

        if (op == CompOp.NE) b = !b;
        // Now we're just eq...

        ConditionalExpression res;
        if (lhs instanceof ConditionalExpression) {
            res = (ConditionalExpression) lhs;
        } else {
            res = new BooleanExpression(lhs);
        }
        if (!b) res = new NotOperation(res);
        return res;
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    @Override
    public void handleStatement(StatementContainer statementContainer) {
        // None of that.
    }
}
