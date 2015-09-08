package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.CommaHelp;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Needs some work here to unify LambdaExpression and LambdaExpressionFallback.
 */
public class LambdaExpressionFallback extends AbstractExpression implements LambdaExpressionCommon {

    private JavaTypeInstance callClassType;
    private String lambdaFnName;
    private List<JavaTypeInstance> targetFnArgTypes;
    private List<Expression> curriedArgs;
    private boolean instance;
    private final boolean colon;


    public LambdaExpressionFallback(JavaTypeInstance callClassType, InferredJavaType castJavaType, String lambdaFnName, List<JavaTypeInstance> targetFnArgTypes, List<Expression> curriedArgs, boolean instance) {
        super(castJavaType);
        this.callClassType = callClassType;
        this.lambdaFnName = lambdaFnName.equals(MiscConstants.INIT_METHOD) ? "new" : lambdaFnName;
        this.targetFnArgTypes = targetFnArgTypes;
        this.curriedArgs = curriedArgs;
        this.instance = instance;
        boolean isColon = false;
        switch (curriedArgs.size()) {
            case 0:
                isColon = targetFnArgTypes.size() <= 1 && !instance;
                if (instance) {
                    /* Don't really understand what's going on here.... */
                    isColon = true;
                    this.instance = false;
                }
                break;
            case 1:
                isColon = targetFnArgTypes.size() == 1 && instance;
                break;
        }
        this.colon = isColon;
    }

    private LambdaExpressionFallback(InferredJavaType inferredJavaType, boolean colon, boolean instance, List<Expression> curriedArgs, List<JavaTypeInstance> targetFnArgTypes, String lambdaFnName, JavaTypeInstance callClassType) {
        super(inferredJavaType);
        this.colon = colon;
        this.instance = instance;
        this.curriedArgs = curriedArgs;
        this.targetFnArgTypes = targetFnArgTypes;
        this.lambdaFnName = lambdaFnName;
        this.callClassType = callClassType;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new LambdaExpressionFallback(getInferredJavaType(), colon, instance, cloneHelper.replaceOrClone(curriedArgs), targetFnArgTypes, lambdaFnName, callClassType);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(targetFnArgTypes);
        collector.collectFrom(curriedArgs);
        collector.collect(callClassType);
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyForwards(curriedArgs, expressionRewriter, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyBackwards(curriedArgs, expressionRewriter, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        if (colon) {
            if (instance) {
                d.dump(curriedArgs.get(0)).print("::").print(lambdaFnName);
            } else {
                d.dump(callClassType).print("::").print(lambdaFnName);
            }
        } else {
            int n = targetFnArgTypes.size();
            boolean multi = n != 1;
            if (multi) d.print("(");
            for (int x = 0; x < n; ++x) {
                if (x > 0) d.print(", ");
                d.print("arg_" + x);
            }
            if (multi) d.print(")");
            if (instance) {
                d.print(" -> ").dump(curriedArgs.get(0)).print('.').print(lambdaFnName);
            } else {
                d.print(" -> ").dump(callClassType).print('.').print(lambdaFnName);
            }
            d.print("(");
            boolean first = true;
            for (int x = instance ? 1 : 0, cnt = curriedArgs.size(); x < cnt; ++x) {
                Expression c = curriedArgs.get(x);
                first = CommaHelp.comma(first, d);
                d.dump(c);
            }
            for (int x = 0; x < n; ++x) {
                first = CommaHelp.comma(first, d);
                d.print("arg_" + x);
            }
            d.print(")");
        }
        return d;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LambdaExpressionFallback that = (LambdaExpressionFallback) o;

        if (colon != that.colon) return false;
        if (instance != that.instance) return false;
        if (callClassType != null ? !callClassType.equals(that.callClassType) : that.callClassType != null)
            return false;
        if (curriedArgs != null ? !curriedArgs.equals(that.curriedArgs) : that.curriedArgs != null) return false;
        if (lambdaFnName != null ? !lambdaFnName.equals(that.lambdaFnName) : that.lambdaFnName != null) return false;
        if (targetFnArgTypes != null ? !targetFnArgTypes.equals(that.targetFnArgTypes) : that.targetFnArgTypes != null)
            return false;

        return true;
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        LambdaExpressionFallback other = (LambdaExpressionFallback) o;
        if (instance != other.instance) return false;
        if (colon != other.colon) return false;
        if (!constraint.equivalent(lambdaFnName, other.lambdaFnName)) return false;
        if (!constraint.equivalent(curriedArgs, other.curriedArgs)) return false;
        return true;
    }


}
