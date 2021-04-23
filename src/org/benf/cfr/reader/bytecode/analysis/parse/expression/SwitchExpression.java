package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredStatementExpression;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class SwitchExpression extends AbstractExpression {
    private Expression value;
    private List<Branch> cases;

    public static class Branch {
        List<Expression> cases;
        Expression value;

        public Branch(List<Expression> cases, Expression value) {
            this.cases = cases;
            this.value = value;
        }

        private Branch rewrite(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            boolean thisChanged = false;
            List<Expression> newCases = ListFactory.newList();
            for (Expression exp : cases) {
                Expression newExp = expressionRewriter.rewriteExpression(exp, ssaIdentifiers, statementContainer, flags);
                if (newExp != exp) {
                    thisChanged = true;
                }
                newCases.add(newExp);
            }
            Expression newValue = expressionRewriter.rewriteExpression(value, ssaIdentifiers, statementContainer, flags);
            if (newValue !=value) {
                thisChanged = true;
            }
            if (!thisChanged) return this;
            return new Branch(newCases, newValue);
        }

    }

    public SwitchExpression(BytecodeLoc loc, InferredJavaType inferredJavaType, Expression value, List<Branch> cases) {
        super(loc, inferredJavaType);
        this.value = value;
        this.cases = cases;
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return BytecodeLoc.combine(this, value);
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
        d.keyword("switch ").separator("(");
        d.dump(value);
        d.separator(") ").separator("{");
        d.newln();
        d.indent(1);
        for (Branch item : cases) {
            boolean first = true;
            List<Expression> cases = item.cases;
            if (cases.isEmpty()) {
                d.keyword("default");
            } else {
                d.keyword("case ");
                for (Expression e : cases) {
                    first = StringUtils.comma(first, d);
                    d.dump(e);
                }
            }
            d.operator(" -> ").dump(item.value);
            if (!(item.value instanceof StructuredStatementExpression)) {
                d.endCodeln();
            }
        }
        d.indent(-1);
        d.separator("}");
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
        List<Branch> out = ListFactory.newList();
        for (Branch case1 : cases) {
            Branch newBranch = case1.rewrite(expressionRewriter, ssaIdentifiers, statementContainer, flags);
            if (newBranch != case1) {
                changed = true;
                out.add(newBranch);
            } else {
                out.add(case1);
            }
        }
        if (changed) {
            return new SwitchExpression(getLoc(), getInferredJavaType(), newValue, out);
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
        for (Branch case1 : cases) {
            for (Expression item : case1.cases) item.collectUsedLValues(lValueUsageCollector);
            case1.value.collectUsedLValues(lValueUsageCollector);
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
            Branch p1 = cases.get(i);
            Branch p2 = other.cases.get(i);
            if (!constraint.equivalent(p1.cases, p2.cases)) return false;
            if (!constraint.equivalent(p1.value, p2.value)) return false;
        }
        return constraint.equivalent(value, other.value);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        List<Branch> res = ListFactory.newList();
        for (Branch case1 : cases) {
            res.add(new Branch(cloneHelper.replaceOrClone(case1.cases), cloneHelper.replaceOrClone(case1.value)));
        }
        return new SwitchExpression(getLoc(), getInferredJavaType(), cloneHelper.replaceOrClone(value), res);
    }
}
