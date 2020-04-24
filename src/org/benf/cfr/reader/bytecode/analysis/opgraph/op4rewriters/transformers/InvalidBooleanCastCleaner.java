package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

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

    private static Expression applyTransforms(CastExpression t) {
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
                return new ComparisonOperation(child, Literal.INT_ZERO, CompOp.NE);
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

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof CastExpression) {
            expression = applyTransforms((CastExpression)expression);
        }
        return expression;
    }
}
