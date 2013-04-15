package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:44
 * <p/>
 * 1d array only.
 */
public class NewAnonymousArray extends AbstractExpression {
    private List<Expression> values;
    private final JavaTypeInstance resultType;

    public NewAnonymousArray(List<Expression> values, JavaTypeInstance resultInstance) {
        super(new InferredJavaType(resultInstance, InferredJavaType.Source.EXPRESSION));
        this.values = values;
        this.resultType = resultInstance;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("new ").append(resultType.getArrayStrippedType()).append("[]{");
        boolean first = true;
        for (Expression value : values) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }
            sb.append(value);
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        for (int x = 0; x < values.size(); ++x) {
            values.set(x, values.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        for (int x = 0; x < values.size(); ++x) {
            values.set(x, expressionRewriter.rewriteExpression(values.get(x), ssaIdentifiers, statementContainer, flags));
        }
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
    }

}
