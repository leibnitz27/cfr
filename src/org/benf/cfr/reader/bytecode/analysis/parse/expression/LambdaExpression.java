package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/04/2013
 * Time: 23:47
 */
public class LambdaExpression extends AbstractExpression {

    private String lambdaFnName;
    private List<JavaTypeInstance> targetFnArgTypes;
    private List<Expression> curriedArgs;
    private boolean instance;


    public LambdaExpression(InferredJavaType castJavaType, String lambdaFnName, List<JavaTypeInstance> targetFnArgTypes, List<Expression> curriedArgs, boolean instance) {
        super(castJavaType);
        this.lambdaFnName = lambdaFnName;
        this.targetFnArgTypes = targetFnArgTypes;
        this.curriedArgs = curriedArgs;
        this.instance = instance;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        throw new UnsupportedOperationException();
    }

    private boolean comma(boolean first, StringBuilder sb) {
        if (!first) {
            sb.append(", ");
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int n = targetFnArgTypes.size();
        if (n > 1) sb.append('(');
        for (int x = 0; x < n; ++x) {
            if (x > 0) sb.append(", ");
            sb.append("arg_").append(x);
        }
        if (n > 1) sb.append(')');
        sb.append(" -> ");
        sb.append(lambdaFnName);
        sb.append('(');
        boolean first = true;
        for (int x = instance ? 1 : 0, cnt = curriedArgs.size(); x < cnt; ++x) {
            Expression c = curriedArgs.get(x);
            first = comma(first, sb);
            sb.append(c.toString());
        }
        for (int x = 0; x < n; ++x) {
            first = comma(first, sb);
            sb.append("arg_").append(x);
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        throw new UnsupportedOperationException();
    }
}
