package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

public class InvalidBooleanCastCleaner extends AbstractExpressionRewriter implements StructuredStatementTransformer {

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

    private static Expression applyCastTransforms1(CastExpression t) {
        JavaTypeInstance castType = t.getInferredJavaType().getJavaTypeInstance();
        Expression child = t.getChild();
        InferredJavaType childIJT = child.getInferredJavaType();
        JavaTypeInstance childType = childIJT.getJavaTypeInstance();
        if (castType == RawJavaType.BOOLEAN) {
            // Casting *TO* a boolean is always suspicious.  Why are we doing it?
            // It is legit in the case of an unboxing, but if the source type is an int stack,
            // we've got confused.
            //
            // At this point, it's questionable as to if we should have introduced another
            // variable by splitting the lifetime, but CFR prefers to be cautious about that.
            if (childType.getStackType() == StackType.INT &&
                    childIJT.getRawType() != RawJavaType.BOOLEAN) {
                // We're treating an integral type as a boolean.
                // Last minute cheeky != 0.
                // This may happen if an optimizer has reused a non-boolean as a boolean.
                // (See SootOptimizationTest).
                // This *could* be done in an extra pass......
                return new ComparisonOperation(BytecodeLoc.NONE, child, Literal.INT_ZERO, CompOp.NE);
            }
        } else if (childType == RawJavaType.BOOLEAN && castType instanceof RawJavaType) {
            // This is only liable to happen with hand crafted bytecode (iload, i2f etc), but it's still annoying!
            RawJavaType rawCastType = (RawJavaType)castType;
            if (child instanceof Literal) {
                TypedLiteral childValue = ((Literal) child).getValue();

                Expression res = Literal.getLiteralOrNull(rawCastType, t.getInferredJavaType(), childValue.getIntValue());
                if (res != null) return res;
            }
        }
        return t;
    }

    private static Expression applyCastTransforms2(CastExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        JavaTypeInstance expressionType = expression.getInferredJavaType().getJavaTypeInstance();
        if (expressionType == RawJavaType.BOOLEAN || expressionType.getStackType() != StackType.INT) return expression;
        Expression child = expression.getChild();
        if (child.getInferredJavaType().getJavaTypeInstance() != RawJavaType.BOOLEAN) return expression;
        // We have an child, which is supposedly a boolean, being cast to a non-boolean, so we've probably messed
        // up somewhere.  eg - (byte)(b & false)
        Expression newChild = BoolCastInnerTransformer.Instance.rewriteExpression(child, ssaIdentifiers,  statementContainer, flags);
        if (newChild != child) {
            return new CastExpression(expression.getLoc(), expression.getInferredJavaType(), newChild, false);
        }
        return expression;
    }

    private static class BoolCastInnerTransformer extends AbstractExpressionRewriter {
        private static BoolCastInnerTransformer Instance = new BoolCastInnerTransformer();

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (expression.getInferredJavaType().getJavaTypeInstance().getStackType() != StackType.INT) return expression;
            boolean isBool = expression.getInferredJavaType().getJavaTypeInstance() == RawJavaType.BOOLEAN;

            // Because we only descend arithmetic operations, we're not going to randomly start breaking boolean functions.
            // This could be more complete, though. :(
            if (expression instanceof ArithmeticOperation) {
                return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
            }

            if (expression instanceof Literal) {
                if (expression.equals(Literal.TRUE)) return Literal.INT_ONE;
                if (expression.equals(Literal.FALSE)) return Literal.INT_ZERO;
            }

            // No idea - this shouldn't be a bool, though. :(
            if (isBool) {
                return new TernaryExpression(expression.getLoc(), new BooleanExpression(expression), Literal.INT_ONE, Literal.INT_ZERO);
            }
            return expression;
        }
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof CastExpression) {
            expression = applyCastTransforms1((CastExpression)expression);
        }

        if (expression instanceof CastExpression) {
            expression = applyCastTransforms2((CastExpression)expression, ssaIdentifiers, statementContainer, flags);
        }
        return expression;
    }

}
