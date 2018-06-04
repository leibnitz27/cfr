package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.BoxingHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.TernaryExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

public class TernaryCastCleaner extends AbstractExpressionRewriter implements StructuredStatementTransformer {

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

    /*
     * A couple of silly transforms we have to apply to ternaries after the fact.
     */
    private static TernaryExpression applyTransforms(TernaryExpression t) {
        InferredJavaType inferredJavaType = t.getInferredJavaType();
        if (inferredJavaType.getJavaTypeInstance().getStackType() != StackType.REF) {
            Expression lhs = t.getLhs();
            Expression rhs = t.getRhs();
            if (lhs instanceof Literal) {
                lhs = ((Literal) lhs).appropriatelyCasted(inferredJavaType);
                return new TernaryExpression(inferredJavaType, t.getCondition(), lhs, rhs);
            } else if (rhs instanceof Literal) {
                rhs = ((Literal) rhs).appropriatelyCasted(inferredJavaType);
                return new TernaryExpression(inferredJavaType, t.getCondition(), lhs, rhs);
            }
            return t;
        }
        // Ok - what if it *is* a ref.
        // Special (NASTY) case -
        // x = a ? (Number)boxedDouble : (Number)boxedInt
        // vs 
        // x = a ? boxedDouble : boxedInt
        // (These ARE different - see ternaryTest5b/c)
        if (BoxingHelper.isBoxedTypeInclNumber(t.getLhs().getInferredJavaType().getJavaTypeInstance()) &&
            BoxingHelper.isBoxedTypeInclNumber(t.getRhs().getInferredJavaType().getJavaTypeInstance()) &&
            !BoxingHelper.isBoxedType(t.getInferredJavaType().getJavaTypeInstance())) {
            InferredJavaType typ = t.getInferredJavaType();
            return new TernaryExpression(t.getInferredJavaType(),
                    t.getCondition(),
                    new CastExpression(typ, t.getLhs()),
                    new CastExpression(typ, t.getRhs()));
        }
        return t;
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof TernaryExpression) {
            expression = applyTransforms((TernaryExpression)expression);
        }
        return expression;
    }
}
