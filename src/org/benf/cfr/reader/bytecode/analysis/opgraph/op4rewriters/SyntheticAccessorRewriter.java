package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.AbstractMatchResultIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchOneOf;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchSequence;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.Matcher;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.ResetAfterTest;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticMutationOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticPostMutationOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticPreMutationOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StackValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StaticFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.SuperFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredReturn;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.AccessFlagMethod;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.collections.SetUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SyntheticAccessorRewriter extends AbstractExpressionRewriter implements Op04Rewriter {

    private final DCCommonState state;
    private final JavaTypeInstance thisClassType;
    private final ExpressionRewriter visbilityRewriter = new VisibiliyDecreasingRewriter();

    public SyntheticAccessorRewriter(DCCommonState state, JavaTypeInstance thisClassType) {
        this.state = state;
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
            expression = rewriteFunctionExpression((StaticFunctionInvokation) expression);
        }
        return expression;
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
    private static final String MUTATION3 = "mutation3";
    private static final String PRE_INC = "preinc";
    private static final String POST_INC = "postinc";
    private static final String PRE_DEC = "predec";
    private static final String POST_DEC = "postdec";
    private static final String SUPER_INVOKE = "superinv";
    private static final String SUPER_RETINVOKE = "superretinv";

    private static boolean validRelationship(JavaTypeInstance type1, JavaTypeInstance type2) {
        Set<JavaTypeInstance> parents1 = SetFactory.newSet();
        type1.getInnerClassHereInfo().collectTransitiveDegenericParents(parents1);
        parents1.add(type1);
        Set<JavaTypeInstance> parents2 = SetFactory.newSet();
        type2.getInnerClassHereInfo().collectTransitiveDegenericParents(parents2);
        parents2.add(type2);
        boolean res = SetUtil.hasIntersection(parents1, parents2);
        return res;
    }

    private Expression rewriteFunctionExpression2(final StaticFunctionInvokation functionInvokation) {
        JavaTypeInstance tgtType = functionInvokation.getClazz();
        // Does tgtType have an inner relationship with this?
        if (!validRelationship(thisClassType, tgtType)) return null;

        ClassFile otherClass = state.getClassFile(tgtType);
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
        Method otherMethod;
        try {
            otherMethod = otherClass.getMethodByPrototype(otherPrototype);
        } catch (NoSuchMethodException e) {
            // Ignore and return.
            return null;
        }
        if (!otherMethod.testAccessFlag(AccessFlagMethod.ACC_STATIC)) return null;
        if (!otherMethod.testAccessFlag(AccessFlagMethod.ACC_SYNTHETIC)) return null;
//                getParameters(otherMethod.getConstructorFlag());

        /* Get the linearized, comment stripped code for the block. */
        if (!otherMethod.hasCodeAttribute()) return null;
        Op04StructuredStatement otherCode = otherMethod.getAnalysis();
        if (otherCode == null) return null;
        List<LocalVariable> methodArgs = otherMethod.getMethodPrototype().getComputedParameters();

        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(otherCode);

        Expression res = tryRewriteAccessor(structuredStatements, otherType, appliedArgs, methodArgs);
        if (res == null) {
            res = tryRewriteFunctionCall(structuredStatements, otherType, appliedArgs, methodArgs);
        }
        if (res != null) {
            otherMethod.hideSynthetic();
            return visbilityRewriter.rewriteExpression(res, null, null, null);
        }

        return null;
    }

    private Expression tryRewriteAccessor(List<StructuredStatement> structuredStatements, JavaTypeInstance otherType,
                                          List<Expression> appliedArgs, List<LocalVariable> methodArgs) {
        WildcardMatch wcm = new WildcardMatch();

        List<Expression> methodExprs = new ArrayList<Expression>();
        for (int x=1;x<methodArgs.size();++x) {
            methodExprs.add(new LValueExpression(methodArgs.get(x)));
        }

        // Try dealing with the class of access$000 etc which are simple accessors / mutators.
        Matcher<StructuredStatement> matcher = new MatchSequence(
                new BeginBlock(null),
                new MatchOneOf(
                        new ResetAfterTest(wcm, RETURN_LVALUE,
                                new StructuredReturn(new LValueExpression(wcm.getLValueWildCard("lvalue")), null)
                        ),
                        new ResetAfterTest(wcm, MUTATION1, new MatchSequence(
                                new StructuredAssignment(wcm.getLValueWildCard("lvalue"), wcm.getExpressionWildCard("rvalue")),
                                new StructuredReturn(new LValueExpression(wcm.getLValueWildCard("lvalue")), null)
                        )),
                        new ResetAfterTest(wcm, MUTATION2, new MatchSequence(
                                new StructuredAssignment(wcm.getLValueWildCard("lvalue"), wcm.getExpressionWildCard("rvalue")),
                                new StructuredReturn(wcm.getExpressionWildCard("rvalue"), null)
                        )),
                        // Java9 compiler inlines += etc....
                        new ResetAfterTest(wcm, MUTATION3,
                                new StructuredReturn(wcm.getArithmeticMutationWildcard("mutation", wcm.getLValueWildCard("lvalue"), wcm.getExpressionWildCard("rvalue")), null)
                        ),
                        new ResetAfterTest(wcm, PRE_INC,
                                new StructuredReturn(new ArithmeticPreMutationOperation(wcm.getLValueWildCard("lvalue"), ArithOp.PLUS), null)
                        ),
                        new ResetAfterTest(wcm, PRE_DEC,
                                new StructuredReturn(new ArithmeticPreMutationOperation(wcm.getLValueWildCard("lvalue"), ArithOp.MINUS), null)
                        ),
                        new ResetAfterTest(wcm, POST_INC,
                                new StructuredReturn(new ArithmeticPostMutationOperation(wcm.getLValueWildCard("lvalue"), ArithOp.PLUS), null)
                        ),
                        new ResetAfterTest(wcm, POST_DEC,
                                new StructuredReturn(new ArithmeticPostMutationOperation(wcm.getLValueWildCard("lvalue"), ArithOp.MINUS), null)
                        ),
                        new ResetAfterTest(wcm, POST_INC,
                                new MatchSequence(
                                        new StructuredExpressionStatement(new ArithmeticPostMutationOperation(wcm.getLValueWildCard("lvalue"), ArithOp.PLUS), false),
                                        new StructuredReturn(new LValueExpression(wcm.getLValueWildCard("lvalue")), null)
                                )
                        ),
                        new ResetAfterTest(wcm, POST_INC,
                                new MatchSequence(
                                        new StructuredAssignment(wcm.getStackLabelWildcard("tmp"), new LValueExpression(wcm.getLValueWildCard("lvalue"))),
                                        new StructuredAssignment(
                                                wcm.getLValueWildCard("lvalue"),
                                                new ArithmeticOperation(new StackValue(wcm.getStackLabelWildcard("tmp")), new Literal(TypedLiteral.getInt(1)), ArithOp.PLUS)
                                        ),
                                        new StructuredReturn(new StackValue(wcm.getStackLabelWildcard("tmp")), null)
                                )
                        ),
                        new ResetAfterTest(wcm, POST_DEC,
                                new MatchSequence(
                                        new StructuredExpressionStatement(new ArithmeticPostMutationOperation(wcm.getLValueWildCard("lvalue"), ArithOp.MINUS), false),
                                        new StructuredReturn(new LValueExpression(wcm.getLValueWildCard("lvalue")), null)
                                )
                        ),
                        new ResetAfterTest(wcm, SUPER_INVOKE,
                                new StructuredExpressionStatement(wcm.getSuperFunction("super", methodExprs), false)
                        ),
                        new ResetAfterTest(wcm, SUPER_RETINVOKE,
                                new StructuredReturn(wcm.getSuperFunction("super", methodExprs), null)
                        )
                ),
                new EndBlock(null)
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
            if (!otherType.equals(staticVariable.getOwningClassType())) return null;
        }

        /*
         * At this point, we should validate that ONLY the passed in arguments are used in rValue.
         * But I'm a coward. ;)
         */
        String matchType = accessorMatchCollector.matchType;

        Map<LValue, LValue> lValueReplacements = MapFactory.newMap();
        Map<Expression, Expression> expressionReplacements = MapFactory.newMap();
        for (int x = 0; x < methodArgs.size(); ++x) {
            LocalVariable methodArg = methodArgs.get(x);
            Expression appliedArg = appliedArgs.get(x);
            if (appliedArg instanceof LValueExpression) {
                LValue appliedLvalue = ((LValueExpression) appliedArg).getLValue();
                lValueReplacements.put(methodArg, appliedLvalue);
            }
            expressionReplacements.put(new LValueExpression(methodArg), appliedArg);
        }
        CloneHelper cloneHelper = new CloneHelper(expressionReplacements, lValueReplacements);

        if (matchType.equals(MUTATION1) || matchType.equals(MUTATION2)) {
            AssignmentExpression assignmentExpression = new AssignmentExpression(accessorMatchCollector.lValue, accessorMatchCollector.rValue);
            return cloneHelper.replaceOrClone(assignmentExpression);
        } else if (matchType.equals(MUTATION3)) {
            Expression mutation = new ArithmeticMutationOperation(accessorMatchCollector.lValue, accessorMatchCollector.rValue, accessorMatchCollector.op);
            return cloneHelper.replaceOrClone(mutation);
        } else if (matchType.equals(RETURN_LVALUE)) {
            return cloneHelper.replaceOrClone(new LValueExpression(accessorMatchCollector.lValue));
        } else if (matchType.equals(PRE_DEC)) {
            Expression res = new ArithmeticPreMutationOperation(accessorMatchCollector.lValue, ArithOp.MINUS);
            return cloneHelper.replaceOrClone(res);
        } else if (matchType.equals(PRE_INC)) {
            Expression res = new ArithmeticPreMutationOperation(accessorMatchCollector.lValue, ArithOp.PLUS);
            return cloneHelper.replaceOrClone(res);
        } else if (matchType.equals(POST_DEC)) {
            Expression res = new ArithmeticPostMutationOperation(accessorMatchCollector.lValue, ArithOp.MINUS);
            return cloneHelper.replaceOrClone(res);
        } else if (matchType.equals(POST_INC)) {
            Expression res = new ArithmeticPostMutationOperation(accessorMatchCollector.lValue, ArithOp.PLUS);
            return cloneHelper.replaceOrClone(res);
        } else if (matchType.equals(SUPER_INVOKE) || matchType.equals(SUPER_RETINVOKE)) {
            SuperFunctionInvokation invoke = (SuperFunctionInvokation) accessorMatchCollector.rValue;
            SuperFunctionInvokation newInvoke = (SuperFunctionInvokation) cloneHelper.replaceOrClone(invoke);
            newInvoke = newInvoke.withCustomName(otherType);
            return newInvoke;
        } else {
            throw new IllegalStateException();
        }
    }

    private class AccessorMatchCollector extends AbstractMatchResultIterator {

        String matchType;
        LValue lValue;
        Expression rValue;
        ArithOp op;

        @Override
        public void collectMatches(String name, WildcardMatch wcm) {
            this.matchType = name;
            this.lValue = wcm.getLValueWildCard("lvalue").getMatch();
            if (matchType.equals(MUTATION1) || matchType.equals(MUTATION2)) {
                this.rValue = wcm.getExpressionWildCard("rvalue").getMatch();
            }
            if (matchType.equals(MUTATION3)) {
                this.rValue = wcm.getExpressionWildCard("rvalue").getMatch();
                this.op = wcm.getArithmeticMutationWildcard( "mutation").getOp().getMatch();
            }
            if (matchType.equals(SUPER_INVOKE) || matchType.equals(SUPER_RETINVOKE)) {
                this.rValue = wcm.getSuperFunction("super").getMatch();
            }
        }
    }

    private static final String STA_SUB1 = "ssub1";
    private static final String STA_FUN1 = "sfun1";

    private Expression tryRewriteFunctionCall(List<StructuredStatement> structuredStatements, JavaTypeInstance otherType,
                                              List<Expression> appliedArgs, List<LocalVariable> methodArgs) {
        WildcardMatch wcm = new WildcardMatch();

        String MEM_SUB1 = "msub1";
        String MEM_FUN1 = "mfun1";
        Matcher<StructuredStatement> matcher = new MatchSequence(
                new BeginBlock(null),
                new MatchOneOf(
                        new ResetAfterTest(wcm, MEM_SUB1,
                                new StructuredExpressionStatement(wcm.getMemberFunction("func", null, false, new LValueExpression(wcm.getLValueWildCard("lvalue")), null), false)
                        ),
                        new ResetAfterTest(wcm, STA_SUB1,
                                new StructuredExpressionStatement(wcm.getStaticFunction("func", otherType, null, null, (List<Expression>) null), false)
                        ),
                        new ResetAfterTest(wcm, MEM_FUN1,
                                new StructuredReturn(wcm.getMemberFunction("func", null, false, new LValueExpression(wcm.getLValueWildCard("lvalue")), null), null)
                        ),
                        new ResetAfterTest(wcm, STA_FUN1,
                                new StructuredReturn(wcm.getStaticFunction("func", otherType, null, null, (List<Expression>) null), null)
                        )
                ),
                new EndBlock(null)
        );

        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        FuncMatchCollector funcMatchCollector = new FuncMatchCollector();

        mi.advance();
        if (!matcher.match(mi, funcMatchCollector)) return null;
        if (funcMatchCollector.matchType == null) return null;

        Map<LValue, LValue> lValueReplacements = MapFactory.newMap();
        Map<Expression, Expression> expressionReplacements = MapFactory.newMap();
        for (int x = 0; x < methodArgs.size(); ++x) {
            LocalVariable methodArg = methodArgs.get(x);
            Expression appliedArg = appliedArgs.get(x);
            if (appliedArg instanceof LValueExpression) {
                LValue appliedLvalue = ((LValueExpression) appliedArg).getLValue();
                lValueReplacements.put(methodArg, appliedLvalue);
            }
            if (methodArg.getInferredJavaType().getJavaTypeInstance().equals(otherType)) {
                if (!appliedArg.getInferredJavaType().getJavaTypeInstance().equals(otherType)) {
                    appliedArg = new CastExpression(methodArg.getInferredJavaType(), appliedArg);
                }
            }
            expressionReplacements.put(new LValueExpression(methodArg), appliedArg);
        }
        CloneHelper cloneHelper = new CloneHelper(expressionReplacements, lValueReplacements);
        return cloneHelper.replaceOrClone(funcMatchCollector.functionInvokation);
    }

    private class FuncMatchCollector extends AbstractMatchResultIterator {

        String matchType;
        LValue lValue;
        StaticFunctionInvokation staticFunctionInvokation;
        MemberFunctionInvokation memberFunctionInvokation;
        Expression functionInvokation;

        @Override
        public void collectMatches(String name, WildcardMatch wcm) {
            this.matchType = name;
            if (matchType.equals(STA_FUN1) || matchType.endsWith(STA_SUB1)) {
                staticFunctionInvokation = wcm.getStaticFunction("func").getMatch();
                functionInvokation = staticFunctionInvokation;
            } else {
                memberFunctionInvokation = wcm.getMemberFunction("func").getMatch();
                functionInvokation = memberFunctionInvokation;
                lValue = wcm.getLValueWildCard("lvalue").getMatch();
            }
        }
    }

    private class VisibiliyDecreasingRewriter extends AbstractExpressionRewriter {
        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (lValue instanceof StaticVariable) {
                StaticVariable sv = (StaticVariable)lValue;
                JavaTypeInstance owning = sv.getOwningClassType();
                if (!thisClassType.getInnerClassHereInfo().isTransitiveInnerClassOf(owning)) {
                    return sv.getNonSimpleCopy();
                }
            }
            return lValue;
        }
    }
}