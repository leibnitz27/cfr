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
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/04/2013
 * Time: 23:47
 * <p/>
 * This shouldn't really exist - we should be able to construct this in terms of function invokations.
 * However, that requires some tidying, so we'll iterate via this.
 */
public class LambdaExpressionFallback extends AbstractExpression {

    private String lambdaFnName;
    private List<JavaTypeInstance> targetFnArgTypes;
    private List<Expression> curriedArgs;
    private boolean instance;


    public LambdaExpressionFallback(InferredJavaType castJavaType, String lambdaFnName, List<JavaTypeInstance> targetFnArgTypes, List<Expression> curriedArgs, boolean instance) {
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
        for (int x = 0; x < curriedArgs.size(); ++x) {
            curriedArgs.set(x, expressionRewriter.rewriteExpression(curriedArgs.get(x), ssaIdentifiers, statementContainer, flags));
        }
        return this;
    }

    private boolean comma(boolean first, Dumper d) {
        if (!first) {
            d.print(", ");
        }
        return false;
    }

    @Override
    public Dumper dump(Dumper d) {
        int n = targetFnArgTypes.size();
        if (n > 1) d.print("(");
        for (int x = 0; x < n; ++x) {
            if (x > 0) d.print(", ");
            d.print("arg_" + x);
        }
        if (n > 1) d.print(")");
        d.print(" -> ").print(lambdaFnName);
        d.print("(");
        boolean first = true;
        for (int x = instance ? 1 : 0, cnt = curriedArgs.size(); x < cnt; ++x) {
            Expression c = curriedArgs.get(x);
            first = comma(first, d);
            d.dump(c);
        }
        for (int x = 0; x < n; ++x) {
            first = comma(first, d);
            d.print("arg_" + x);
        }
        d.print(")");
        return d;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        throw new UnsupportedOperationException();
    }
}
