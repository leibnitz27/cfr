package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredStatementExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredThrow;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class SwitchExpression extends AbstractExpression {
    private Expression value;
    private List<Pair<List<Expression>, Expression>> cases;

    public SwitchExpression(InferredJavaType inferredJavaType, Expression value, List<Pair<List<Expression>, Expression>> cases) {
        super(inferredJavaType);
        this.value = value;
        this.cases = cases;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof SwitchExpression)) return false;
        SwitchExpression other = (SwitchExpression)o;
        return cases.equals(other.cases) && value.equals(other.value);
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.WEAKEST;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        d.print("switch (");
        d.dump(value);
        d.print(") {");
        d.newln();
        d.indent(1);
        for (Pair<List<Expression>, Expression> item : cases) {
            boolean first = true;
            List<Expression> cases = item.getFirst();
            if (cases.isEmpty()) {
                d.print("default");
            } else {
                for (Expression e : cases) {
                    first = StringUtils.comma(first, d);
                    d.dump(e);
                }
            }
            d.print(" -> ").dump(item.getSecond());
            if (!(item.getSecond() instanceof StructuredStatementExpression)) {
                d.print(';').newln();
            }
        }
        d.indent(-1);
        d.print("}");
        return d;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        boolean changed = false;
        Expression newValue = expressionRewriter.rewriteExpression(value, ssaIdentifiers, statementContainer, flags);
        if (newValue != value) {
            changed = true;
        }
        List<Pair<List<Expression>, Expression>> out = ListFactory.newList();
        for (Pair<List<Expression>, Expression> case1 : cases) {
            List<Expression> exprs = ListFactory.newList();
            boolean thisChanged = false;
            for (Expression exp : case1.getFirst()) {
                Expression newExp = expressionRewriter.rewriteExpression(exp, ssaIdentifiers, statementContainer, flags);
                if (newExp != exp) {
                    thisChanged = true;
                }
                exprs.add(newExp);
            }
            Expression new1 = expressionRewriter.rewriteExpression(case1.getSecond(), ssaIdentifiers, statementContainer, flags);
            if (new1 != case1.getSecond()) {
                thisChanged = true;
            }
            if (thisChanged) {
                changed = true;
                out.add(Pair.make(exprs, new1));
            } else {
                out.add(case1);
            }
        }
        if (changed) {
            return new SwitchExpression(getInferredJavaType(), newValue, out);
        }
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        value.collectUsedLValues(lValueUsageCollector);
        for (Pair<List<Expression>, Expression> case1 : cases) {
            for (Expression item : case1.getFirst()) item.collectUsedLValues(lValueUsageCollector);
            case1.getSecond().collectUsedLValues(lValueUsageCollector);
        }
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof SwitchExpression)) return false;
        SwitchExpression other = (SwitchExpression)o;
        if (other.cases.size() != cases.size()) return false;
        for (int i=0;i<cases.size();++i) {
            Pair<List<Expression>, Expression> p1 = cases.get(i);
            Pair<List<Expression>, Expression> p2 = other.cases.get(i);
            if (!constraint.equivalent(p1.getFirst(), p2.getFirst())) return false;
            if (!constraint.equivalent(p1.getSecond(), p2.getSecond())) return false;
        }
        return constraint.equivalent(value, other.value);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        List<Pair<List<Expression>, Expression>> res = ListFactory.newList();
        for (Pair<List<Expression>, Expression> case1 : cases) {
            res.add(Pair.make(cloneHelper.replaceOrClone(case1.getFirst()), cloneHelper.replaceOrClone(case1.getSecond())));
        }
        return new SwitchExpression(getInferredJavaType(), cloneHelper.replaceOrClone(value), res);
    }
}
