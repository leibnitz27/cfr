package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.CondenseConditionals;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.ExpressionReplacingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.collections.ListFactory;

import java.util.List;

public class InstanceOfAssignRewriter {
    private static final InferredJavaType ijtBool = new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.EXPRESSION);

    private final WildcardMatch wcm = new WildcardMatch();
    private final LValue scopedEntity;
    private final WildcardMatch.LValueWildcard objWildcard;
    private final WildcardMatch.LValueWildcard tmpWildcard;
    private final List<ConditionTest> tests;
    private final WildcardMatch.DeepAssignment deepAssign;

    public static boolean hasInstanceOf(ConditionalExpression conditionalExpression) {
        InstanceOfSearch search = new InstanceOfSearch();
        search.rewriteExpression(conditionalExpression, null, null, null);
        return search.found;
    }

    private static class InstanceOfSearch extends AbstractExpressionRewriter {
        private boolean found = false;

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (found) return expression;
            if (expression instanceof InstanceOfExpression) {
                found = true;
                return expression;
            }
            return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        }

        @Override
        public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (found) return expression;
            return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        }
    }

    private enum MatchType {
        SIMPLE_J14,
        ASSIGN_SIMPLE_J14,
        SIMPLE_J16
    }

    private static class ConditionTest {
        final ConditionalExpression expression;
        final boolean isPositive;
        final MatchType matchType;

        ConditionTest(ConditionalExpression ct, boolean isPositive, MatchType matchType) {
            this.expression = ct;
            this.isPositive = isPositive;
            this.matchType = matchType;
        }
    }

    // a && b
    // matches (a && b) && c
    // or c && (a && b)
    // but how do we handle (c && a) && b?
    public InstanceOfAssignRewriter(LValue scopedEntity) {

        JavaTypeInstance target = scopedEntity.getInferredJavaType().getJavaTypeInstance();
        this.scopedEntity = scopedEntity;
        InferredJavaType ijtTarget = new InferredJavaType(target, InferredJavaType.Source.EXPRESSION);
        objWildcard = wcm.getLValueWildCard("obj");
        tmpWildcard = wcm.getLValueWildCard("tmp");
        LValueExpression obj = new LValueExpression(objWildcard);
        CastExpression castObj = new CastExpression(BytecodeLoc.TODO, ijtTarget, obj);

        // Simple conditional tests.
        // a instanceof Foo && (x = (Foo)a) == a
        // --> a instanceof Foo x
        BooleanExpression instanceObj = new BooleanExpression(new InstanceOfExpression(BytecodeLoc.NONE, ijtBool, obj, target));

        tests = ListFactory.newList();
        ConditionalExpression cPos1 = new BooleanOperation(BytecodeLoc.NONE,
                instanceObj,
                new ComparisonOperation(BytecodeLoc.NONE, new AssignmentExpression(BytecodeLoc.NONE, scopedEntity, castObj), castObj, CompOp.EQ),
                BoolOp.AND
        );
        ConditionalExpression cPos2 = new NotOperation(BytecodeLoc.NONE, cPos1.getDemorganApplied(true));
        tests.add(new ConditionTest(cPos1, true, MatchType.SIMPLE_J14));
        tests.add(new ConditionTest(cPos2, true, MatchType.SIMPLE_J14));
        tests.add(new ConditionTest(cPos1.getNegated(), false, MatchType.SIMPLE_J14));
        tests.add(new ConditionTest(cPos2.getNegated(), false, MatchType.SIMPLE_J14));

        // Assignment conditional tests.
        // (a = y) instanceOf Foo && (x = (Foo)a) == a
        // --> (a = y) instanceof Foo x
        // The inline assignment is ALMOST CERTAINLY pointless, but unless we can prove it's not used
        // anywhere, we need to retain it. (the existence of 'a' is an annoying JDK artifact).
        CastExpression castTmp = new CastExpression(BytecodeLoc.NONE, ijtTarget, new LValueExpression(tmpWildcard));

        ConditionalExpression dPos1 = new BooleanOperation(BytecodeLoc.NONE,
                new BooleanExpression(new InstanceOfExpression(BytecodeLoc.NONE, ijtBool, new AssignmentExpression(BytecodeLoc.NONE, tmpWildcard, obj), target)),
                new ComparisonOperation(BytecodeLoc.NONE, new AssignmentExpression(BytecodeLoc.NONE, scopedEntity, castTmp), castTmp, CompOp.EQ),
                BoolOp.AND
        );
        ConditionalExpression dPos2 = new NotOperation(BytecodeLoc.NONE, cPos1.getDemorganApplied(true));
        tests.add(new ConditionTest(dPos1, true, MatchType.ASSIGN_SIMPLE_J14));
        tests.add(new ConditionTest(dPos2, true, MatchType.ASSIGN_SIMPLE_J14));
        tests.add(new ConditionTest(dPos1.getNegated(), false, MatchType.ASSIGN_SIMPLE_J14));
        tests.add(new ConditionTest(dPos2.getNegated(), false, MatchType.ASSIGN_SIMPLE_J14));

        // Later versions of java make the generated code a lot simpler, which is perversely harder to spot.
        // obj instanceof Foo && SOMEEXPRESSIONINVOLVING( x = (Foo)obj )
        // eg:
        // obj instanceof Foo && ( x = (Foo)obj ).bob()
        //

        // We want ANY assignment of the obj
        // if (obj instanceof Foo && (x = obj).bar() )
        // if (obj instanceof Foo && 1 < (x = obj).bar() )
        // if (obj instanceof Foo && fn(x = obj) )
        // if (obj instanceof Foo && f(fn(x = obj) > 2) )
        // So we need to have the 2nd conditional be a custom search, that ensures x is assigned from obj,
        // before it is used.

        WildcardMatch.DeepAssignment j16Ce1 = wcm.findDeepAssignment("assignSearch", obj);
        this.deepAssign = j16Ce1;
        ConditionalExpression j16Pos1 = new BooleanOperation(BytecodeLoc.NONE, instanceObj, j16Ce1, BoolOp.AND);
        tests.add(new ConditionTest(j16Pos1, true, MatchType.SIMPLE_J16));

    }

    private ConditionTest getMatchingTest(ConditionalExpression ce) {
        for (ConditionTest ct : tests) {
            wcm.reset();
            if (ct.expression.equals(ce)) {
                return ct;
            }
        }
        return null;
    }

    public boolean isMatchFor(ConditionalExpression ce) {
        RewriteFinder rewriteFinder = new RewriteFinder();
        rewriteFinder.rewriteExpression(ce, null, null, null);
        return rewriteFinder.found;
    }

    private class RewriteFinder extends AbstractExpressionRewriter {
        private boolean found = false;
        @Override
        public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (found) return expression;
            if (getMatchingTest(expression) != null) {
                found = true;
                return expression;
            }
            return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        }

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (found) return expression;
            return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        }
    }

    private class Rewriter extends AbstractExpressionRewriter {
        @Override
        public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            expression = rewriteInner(expression);
            return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        }
    }

    public ConditionalExpression rewriteDefining(ConditionalExpression ce) {
        return new Rewriter().rewriteExpression(ce, null, null, null);
    }

    private ConditionalExpression rewriteInner(ConditionalExpression ce) {
        ConditionTest ct = getMatchingTest(ce);
        if (ct == null) {
            return ce;
        }

        if (ct.matchType == MatchType.SIMPLE_J14) {
            LValue obj = objWildcard.getMatch();

            ce = new BooleanExpression(new InstanceOfExpressionDefining(BytecodeLoc.TODO,
                    new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.EXPRESSION),
                    new LValueExpression(obj),
                    scopedEntity.getInferredJavaType().getJavaTypeInstance(),
                    scopedEntity
            ));
        } else if (ct.matchType == MatchType.ASSIGN_SIMPLE_J14) {
            LValue obj = objWildcard.getMatch();
            LValue tmp = tmpWildcard.getMatch();

            ce = new BooleanExpression(new InstanceOfExpressionDefining(BytecodeLoc.TODO,
                    new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.EXPRESSION),
                    new AssignmentExpression(BytecodeLoc.TODO,tmp, new LValueExpression(BytecodeLoc.TODO,obj)),
                    scopedEntity.getInferredJavaType().getJavaTypeInstance(),
                    scopedEntity
            ));
        } else if (ct.matchType == MatchType.SIMPLE_J16) {
            LValue obj = objWildcard.getMatch();
            // Need to keep both sides of existing ce, and MUTATE.

            AssignmentExpression originalAssign = this.deepAssign.getFoundAssignment();

            BooleanOperation bo = (BooleanOperation)ce;

            ConditionalExpression newLhs = new BooleanExpression(new InstanceOfExpressionDefining(BytecodeLoc.TODO,
                    new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.EXPRESSION),
                    new LValueExpression(obj),
                    scopedEntity.getInferredJavaType().getJavaTypeInstance(),
                    scopedEntity
            ));
            ConditionalExpression newRhs = new ExpressionReplacingRewriter(originalAssign, new LValueExpression(scopedEntity)).rewriteExpression(bo.getRhs(), null, null, null);
            // `scopedEntity` is assigned by instanceof and is therefore known to be non-null
            if (CondenseConditionals.isRedundantInstanceOfNullCheck(newRhs, scopedEntity)) {
                // Ignore the redundant newRhs
                ce = newLhs;
            } else {
                ce = new BooleanOperation(BytecodeLoc.NONE, newLhs, newRhs, bo.getOp());
            }
        }
        if (!ct.isPositive) {
            ce = ce.getNegated();
        }
        return ce;
    }
}
