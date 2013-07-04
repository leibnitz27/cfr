package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
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
 */
public class LambdaExpression extends AbstractExpression {

    private List<LValue> args;
    private Expression result;

    public LambdaExpression(InferredJavaType castJavaType, List<LValue> args, Expression result) {
        super(castJavaType);
        this.args = args;
        this.result = result;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new LambdaExpression(getInferredJavaType(), cloneHelper.replaceOrClone(args), cloneHelper.replaceOrClone(result));
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, expressionRewriter.rewriteExpression(args.get(x), ssaIdentifiers, statementContainer, flags));
        }
        result = expressionRewriter.rewriteExpression(result, ssaIdentifiers, statementContainer, flags);
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
        boolean multi = args.size() != 1;
        boolean first = true;
        if (multi) d.print("(");
        for (LValue lValue : args) {
            first = comma(first, d);
            d.dump(lValue);
        }
        if (multi) d.print(")");
        return d.print(" -> ").dump(result);
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        throw new UnsupportedOperationException();
    }
}
