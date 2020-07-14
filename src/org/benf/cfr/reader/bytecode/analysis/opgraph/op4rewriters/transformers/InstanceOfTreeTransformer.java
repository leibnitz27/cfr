package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BoolOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.InstanceOfExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredIf;

public class InstanceOfTreeTransformer implements StructuredStatementTransformer {

    public void transform(Op04StructuredStatement root) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        in.transformStructuredChildren(this, scope);
        if (in instanceof StructuredIf) {
            InstanceTreeRewriter instanceTreeRewriter = new InstanceTreeRewriter();
            in.rewriteExpressions(instanceTreeRewriter);
        }
        return in;
    }

    private class InstanceTreeRewriter extends AbstractExpressionRewriter {
        @Override
        public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (expression instanceof BooleanOperation) {
                BooleanOperation bo = (BooleanOperation)expression;
                if (bo.getOp() == BoolOp.AND && bo.getLhs() instanceof BooleanExpression && bo.getRhs() instanceof BooleanOperation) {
                    BooleanExpression bol = (BooleanExpression)bo.getLhs();
                    BooleanOperation bor = (BooleanOperation)bo.getRhs();
                    if (bor.getOp() == BoolOp.AND && bol.getInner() instanceof InstanceOfExpression) {
                        expression = new BooleanOperation(expression.getLoc(),
                                new BooleanOperation(expression.getLoc(), bo.getLhs(), bor.getLhs(), BoolOp.AND),
                                bor.getRhs(), BoolOp.AND);
                    }
                }
            }
            return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        }
    }

}
