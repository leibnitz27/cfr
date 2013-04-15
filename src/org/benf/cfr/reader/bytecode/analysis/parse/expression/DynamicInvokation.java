package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/04/2013
 * Time: 23:47
 */
public class DynamicInvokation extends AbstractExpression {
    private Expression innerInvokation;
    private List<Expression> dynamicArgs;

    public DynamicInvokation(InferredJavaType castJavaType, Expression innerInvokation, List<Expression> dynamicArgs) {
        super(castJavaType);
        this.innerInvokation = innerInvokation;
        this.dynamicArgs = dynamicArgs;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        innerInvokation.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        for (int x = 0; x < dynamicArgs.size(); ++x) {
            dynamicArgs.set(x, dynamicArgs.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        innerInvokation.applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
        for (int x = 0; x < dynamicArgs.size(); ++x) {
            dynamicArgs.set(x, expressionRewriter.rewriteExpression(dynamicArgs.get(x), ssaIdentifiers, statementContainer, flags));
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(getInferredJavaType().getCastString()).append(")");
        sb.append(innerInvokation.toString());
        sb.append("(");
        boolean first = true;
        for (Expression arg : dynamicArgs) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(arg.toString());
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        innerInvokation.collectUsedLValues(lValueUsageCollector);
        for (Expression expression : dynamicArgs) {
            expression.collectUsedLValues(lValueUsageCollector);
        }
    }

    public Expression getInnerInvokation() {
        return innerInvokation;
    }

    public List<Expression> getDynamicArgs() {
        return dynamicArgs;
    }
}
