package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.AccessFlagMethod;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.Predicate;
import org.benf.cfr.reader.util.getopt.CFRState;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2013
 * Time: 06:26
 */
public class SyntheticAccessorRewriter implements Op04Rewriter, ExpressionRewriter {

    private final CFRState cfrState;
    private final JavaTypeInstance thisClassType;

    public SyntheticAccessorRewriter(CFRState cfrState, JavaTypeInstance thisClassType) {
        this.cfrState = cfrState;
        this.thisClassType = thisClassType;
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        for (StructuredStatement statement : structuredStatements) {
            statement.rewriteExpressions(this);
        }
    }

    /*
     * Expression rewriter boilerplate - note that we can't expect ssaIdentifiers to be non-null.
     */
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        // TODO : In practice, the rewrites are ALWAYS done in terms of static functions.
        // TODO : should we assume this?  Seems like a good thing an obfuscator could use.
        expression = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof StaticFunctionInvokation) {
            /*
             * REWRITE INSIDE OUT! First, rewrite args, THEN rewrite expression.
             */
            return rewriteFunctionExpression((StaticFunctionInvokation) expression);
        }
        return expression;
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (ConditionalExpression) res;
    }

    @Override
    public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (AbstractAssignmentExpression) res;
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    @Override
    public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    private Expression rewriteFunctionExpression(final StaticFunctionInvokation functionInvokation) {
        Expression res = rewriteFunctionExpression2(functionInvokation);
        // Just a cheat to allow me to return null.
        if (res == null) return functionInvokation;
        return res;
    }

    private static final String RETURN_LVALUE = "returnlvalue";
    private static final String MUTATION1 = "mutation1";
    private static final String MUTATION2 = "mutation2";
    private static final String P_MUTATION = "pmutation";

    private Expression rewriteFunctionExpression2(final StaticFunctionInvokation functionInvokation) {
        JavaTypeInstance tgtType = functionInvokation.getClazz();
        // Does tgtType have an inner relationship with this?
        boolean child = thisClassType.getInnerClassHereInfo().isTransitiveInnerClassOf(tgtType);
        boolean parent = tgtType.getInnerClassHereInfo().isInnerClassOf(thisClassType);
        if (!(child || parent)) return null;

        ClassFile otherClass = cfrState.getClassFile(tgtType, true);
        JavaTypeInstance otherType = otherClass.getClassType();
        MethodPrototype otherPrototype = functionInvokation.getFunction().getMethodPrototype();
        List<Expression> appliedArgs = functionInvokation.getArgs();

        /*
         * Look at the code for the referenced method.
         *
         * It will either be
         *
         *  * a static getter (0 args)
         *  * a static mutator (1 arg)
         *  * an instance getter (1 arg)
         *  * an instance mutator (2 args)
         *
         * Either way, the accessor method SHOULD be a static synthetic.
         */
        Method otherMethod = null;
        try {
            otherMethod = otherClass.getMethodByPrototype(otherPrototype);
        } catch (NoSuchMethodException e) {
            // Ignore and return.
            return null;
        }
        if (!otherMethod.testAccessFlag(AccessFlagMethod.ACC_STATIC)) return null;
        if (!otherMethod.testAccessFlag(AccessFlagMethod.ACC_SYNTHETIC)) return null;
        List<LocalVariable> methodArgs = otherMethod.getMethodPrototype().getParameters();

        /* Get the linearized, comment stripped code for the block. */
        if (!otherMethod.hasCodeAttribute()) return null;
        Op04StructuredStatement otherCode = otherMethod.getAnalysis();
        if (otherCode == null) return null;

        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(otherCode);

        WildcardMatch wcm = new WildcardMatch();


        Matcher<StructuredStatement> matcher = new MatchSequence(
                new BeginBlock(),
                new MatchOneOf(
                        new ResetAfterTest(wcm, RETURN_LVALUE,
                                new StructuredReturn(new LValueExpression(wcm.getLValueWildCard("lvalue")))
                        ),
                        new ResetAfterTest(wcm, MUTATION1, new MatchSequence(
                                new StructuredAssignment(wcm.getLValueWildCard("lvalue"), wcm.getExpressionWildCard("rvalue")),
                                new StructuredReturn(new LValueExpression(wcm.getLValueWildCard("lvalue")))
                        )),
                        new ResetAfterTest(wcm, MUTATION2, new MatchSequence(
                                new StructuredAssignment(wcm.getLValueWildCard("lvalue"), wcm.getExpressionWildCard("rvalue")),
                                new StructuredReturn(wcm.getExpressionWildCard("rvalue"))
                        )),
                        new ResetAfterTest(wcm, P_MUTATION, new MatchSequence(
                                new MatchOneOf(
                                        new StructuredExpressionStatement(new ArithmeticPreMutationOperation(wcm.getLValueWildCard("lvalue"), ArithOp.PLUS), false),
                                        new StructuredExpressionStatement(new ArithmeticPreMutationOperation(wcm.getLValueWildCard("lvalue"), ArithOp.MINUS), false),
                                        new StructuredExpressionStatement(new ArithmeticPostMutationOperation(wcm.getLValueWildCard("lvalue"), ArithOp.PLUS), false),
                                        new StructuredExpressionStatement(new ArithmeticPostMutationOperation(wcm.getLValueWildCard("lvalue"), ArithOp.MINUS), false)
                                ),
                                new StructuredReturn(new LValueExpression(wcm.getLValueWildCard("lvalue")))
                        ))
                ),
                new EndBlock()
        );

        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        AccessorMatchCollector accessorMatchCollector = new AccessorMatchCollector();

        mi.advance();
        if (!matcher.match(mi, accessorMatchCollector)) return null;
        if (accessorMatchCollector.matchType == null) return null;

        boolean isStatic = (accessorMatchCollector.lValue instanceof StaticVariable);
        if (isStatic) {
            // let's be paranoid, and make sure that it's a static on the accessor class.
            StaticVariable staticVariable = (StaticVariable) accessorMatchCollector.lValue;
            if (!otherType.equals(staticVariable.getOwningClassTypeInstance())) return null;
        }

        /*
         * At this point, we should validate that ONLY the passed in arguments are used in rValue.
         * But I'm a coward. ;)
         */
        String matchType = accessorMatchCollector.matchType;
        Map<LValue, LValue> lValueReplacements = MapFactory.newMap();
        Map<Expression, Expression> expressionReplacements = MapFactory.newMap();

        if (!isStatic) {
            /*
             * Verify that arg0 is the foreign type
             */
            if (appliedArgs.isEmpty()) return null;
            if (!appliedArgs.get(0).getInferredJavaType().getJavaTypeInstance().equals(otherType)) return null;
            /*
             * replace arg(0) with appliedargs(0).
             */
            Expression arg0 = appliedArgs.get(0);
            if (!(arg0 instanceof LValueExpression)) return null;
            lValueReplacements.put(methodArgs.get(0), ((LValueExpression) arg0).getLValue());
        }

        CloneHelper cloneHelper = new CloneHelper(expressionReplacements, lValueReplacements);
        if (matchType.equals(MUTATION1) || matchType.equals(MUTATION2)) {
            int rValue = isStatic ? 0 : 1;
            if (appliedArgs.size() != (rValue + 1)) return null;
            expressionReplacements.put(new LValueExpression(methodArgs.get(rValue)), appliedArgs.get(rValue));

            AssignmentExpression assignmentExpression = new AssignmentExpression(accessorMatchCollector.lValue, accessorMatchCollector.rValue, true);
            otherMethod.hideSynthetic();
            return cloneHelper.replaceOrClone(assignmentExpression);
        } else if (matchType.equals(RETURN_LVALUE)) {
            otherMethod.hideSynthetic();
            return cloneHelper.replaceOrClone(new LValueExpression(accessorMatchCollector.lValue));
        } else if (matchType.equals(P_MUTATION)) {
            return null;
        } else {
            throw new IllegalStateException();
        }
    }

    private class AccessorMatchCollector implements MatchResultCollector {

        String matchType;
        LValue lValue;
        Expression rValue;

        @Override
        public void clear() {

        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {

        }

        @Override
        public void collectMatches(String name, WildcardMatch wcm) {
            this.matchType = name;
            this.lValue = wcm.getLValueWildCard("lvalue").getMatch();
            if (matchType.equals(MUTATION1) || matchType.equals(MUTATION2)) {
                this.rValue = wcm.getExpressionWildCard("rvalue").getMatch();
            }
        }
    }
}
