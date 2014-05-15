package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class DynamicInvokation extends AbstractExpression {
    private Expression innerInvokation;
    private List<Expression> dynamicArgs;

    public DynamicInvokation(InferredJavaType castJavaType, Expression innerInvokation, List<Expression> dynamicArgs) {
        super(castJavaType);
        this.innerInvokation = innerInvokation;
        this.dynamicArgs = dynamicArgs;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new DynamicInvokation(getInferredJavaType(), cloneHelper.replaceOrClone(innerInvokation), cloneHelper.replaceOrClone(dynamicArgs));
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collectFrom(innerInvokation);
        collector.collectFrom(dynamicArgs);
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
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        d.print("(").dump(getInferredJavaType().getJavaTypeInstance()).print(")");
        d.dump(innerInvokation);
        d.print("(");
        boolean first = true;
        for (Expression arg : dynamicArgs) {
            if (!first) d.print(", ");
            first = false;
            d.dump(arg);
        }
        d.print(")");
        return d;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DynamicInvokation that = (DynamicInvokation) o;

        if (dynamicArgs != null ? !dynamicArgs.equals(that.dynamicArgs) : that.dynamicArgs != null) return false;
        if (innerInvokation != null ? !innerInvokation.equals(that.innerInvokation) : that.innerInvokation != null)
            return false;

        return true;
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        DynamicInvokation other = (DynamicInvokation) o;
        if (!constraint.equivalent(innerInvokation, other.innerInvokation)) return false;
        if (!constraint.equivalent(dynamicArgs, other.dynamicArgs)) return false;
        return true;
    }

}
