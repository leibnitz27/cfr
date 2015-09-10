package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

public class BadCastChainRewriter extends AbstractExpressionRewriter {
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression = super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof CastExpression) {
            Expression child = ((CastExpression) expression).getChild();
            JavaTypeInstance type = expression.getInferredJavaType().getJavaTypeInstance().getDeGenerifiedType();
            JavaTypeInstance childType = child.getInferredJavaType().getJavaTypeInstance().getDeGenerifiedType();
            if (type.isComplexType() && childType.isComplexType()) {
                if (!childType.correctCanCastTo(type, null)) {
                    expression = new CastExpression(
                        expression.getInferredJavaType(),
                        new CastExpression(new InferredJavaType(TypeConstants.OBJECT, InferredJavaType.Source.UNKNOWN),
                                child, true)
                    );
                }
            }
        }
        return expression;
    }
}
