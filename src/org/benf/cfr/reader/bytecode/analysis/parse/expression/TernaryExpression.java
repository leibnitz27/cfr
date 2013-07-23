package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:51
 * To change this template use File | Settings | File Templates.
 */
public class TernaryExpression extends AbstractExpression implements BoxingProcessor {
    private ConditionalExpression condition;
    private Expression lhs;
    private Expression rhs;

    public TernaryExpression(ConditionalExpression condition, Expression lhs, Expression rhs) {
        super(inferredType(lhs.getInferredJavaType(), rhs.getInferredJavaType()));
        this.condition = condition;
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new TernaryExpression((ConditionalExpression) cloneHelper.replaceOrClone(condition), cloneHelper.replaceOrClone(lhs), cloneHelper.replaceOrClone(rhs));
    }

    private static InferredJavaType inferredType(InferredJavaType a, InferredJavaType b) {
        // We know these types are the same (any cast will cause a break in the inferred type
        // chain).
        b.chain(a);
        return a;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print("(").dump(condition).print(") ? (").dump(lhs).print(") : (").dump(rhs).print(")");
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        Expression replacementCondition = condition.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        if (replacementCondition != condition) throw new ConfusedCFRException("Can't yet support replacing conditions");
        lhs = lhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        rhs = rhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);

        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        condition = expressionRewriter.rewriteExpression(condition, ssaIdentifiers, statementContainer, flags);
        lhs = expressionRewriter.rewriteExpression(lhs, ssaIdentifiers, statementContainer, flags);
        rhs = expressionRewriter.rewriteExpression(rhs, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        condition.collectUsedLValues(lValueUsageCollector);
        lhs.collectUsedLValues(lValueUsageCollector);
        rhs.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public Dumper dumpWithOuterPrecedence(Dumper d, int outerPrecedence) {
        return d.print("(").dump(this).print(")");
    }

    @Override
    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        if (boxingRewriter.isUnboxedType(lhs)) {
            rhs = boxingRewriter.sugarUnboxing(rhs);
            return false;
        }
        if (boxingRewriter.isUnboxedType(rhs)) {
            lhs = boxingRewriter.sugarUnboxing(lhs);
            return false;
        }

        return false;
    }
}
