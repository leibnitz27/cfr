package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.ExpressionReplacingRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.LValueReplacingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.SentinelLocalClassLValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredStatementExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredDefinition;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class LocalUsageCheck extends AbstractExpressionRewriter implements StructuredStatementTransformer, ExpressionRewriter {
    int count = 0;
    private final Set<LValue> lv = SetFactory.newSet();
    private final Map<LValue, Integer> usages = MapFactory.newMap();
    private final Map<LValue, LValue> replacementsL = MapFactory.newMap();
    private final Map<LValue, Pair<Expression, Expression>> replacementsR = MapFactory.newMap();
    private final Map<LValue, StatementContainer> contained = MapFactory.newMap();

    public LocalUsageCheck(Collection<LValue> lvs) {
        lv.addAll(lvs);
        for (LValue l : lvs) {
            usages.put(l, 0);
        }
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        in.transformStructuredChildren(this, scope);
        in.rewriteExpressions(this);
        return in;
    }

    // Chances are this is going to be an assignment.
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof AssignmentExpression) {
            AssignmentExpression s = (AssignmentExpression)expression;
            LValue lve = s.getlValue();
            Expression e = s.getrValue();
            if (lv.contains(lve)) {
                usages.put(lve, usages.get(lve) + 1);
                replacementsR.put(lve, Pair.make(expression, e));
                contained.put(lve, statementContainer);
            }
            super.rewriteExpression(e, ssaIdentifiers, statementContainer, flags);
            return expression;
        } else {
            return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        }
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (lv.contains(lValue)) {
            usages.put(lValue, usages.get(lValue) + 1);
        }
        return lValue;
    }

    @Override
    public void handleStatement(StatementContainer statementContainer) {
        if (statementContainer.getStatement() instanceof StructuredAssignment) {
            StructuredAssignment sa = (StructuredAssignment) statementContainer.getStatement();
            LValue lve = sa.getLvalue();
            if (lv.contains(lve)) {
                // increment will happen by the expression rewriter.
                Expression e = sa.getRvalue();
                if (e instanceof AssignmentExpression) {
                    replacementsL.put(lve, ((AssignmentExpression) e).getlValue());
                    replacementsR.put(lve, Pair.make(e, ((AssignmentExpression) e).getrValue()));
                    contained.put(lve, statementContainer);
                }
            }
        }
    }

    public int usage(LValue lv) {
        return usages.get(lv);
    }

    public boolean rewriteAway(LValue lv) {
        boolean res = false;
        if (replacementsL.containsKey(lv)) {
            Map<LValue, LValue> tmp = MapFactory.newMap();
            tmp.put(lv, replacementsL.get(lv));
            LValueReplacingRewriter e = new LValueReplacingRewriter(tmp);
            ((Op04StructuredStatement)contained.get(lv)).getStatement().rewriteExpressions(e);
            res = true;
        }
        if (replacementsR.containsKey(lv)) {
            Pair<Expression, Expression> p = replacementsR.get(lv);
            ExpressionRewriter e = new ExpressionReplacingRewriter(p.getFirst(), p.getSecond());
            ((Op04StructuredStatement)contained.get(lv)).getStatement().rewriteExpressions(e);
            res = true;
        }
        return res;
    }
}
