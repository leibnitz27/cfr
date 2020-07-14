package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.InfiniteAssertRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.PreconditionAssertRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredStatementExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.List;
import java.util.Map;

public class AssertRewriter {

    private final ClassFile classFile;
    private StaticVariable assertionStatic = null;
    private final boolean switchExpressions;
    private InferredJavaType boolIjt = new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.EXPRESSION);

    public AssertRewriter(ClassFile classFile, Options options) {
        this.classFile = classFile;
        this.switchExpressions = options.getOption(OptionsImpl.SWITCH_EXPRESSION, classFile.getClassFileVersion());
    }

    public void sugarAsserts(Method staticInit) {
        /*
         * Determine if the static init has a line like
         *
         *         CLASSX.y = !(CLASSX.class.desiredAssertionStatus());
         *
         * where y is static final boolean.
         */
        if (!staticInit.hasCodeAttribute()) return;
        List<StructuredStatement> statements = MiscStatementTools.linearise(staticInit.getAnalysis());
        if (statements == null) return;
        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(statements);
        WildcardMatch wcm1 = new WildcardMatch();

        final JavaTypeInstance topClassType = classFile.getClassType();
        InnerClassInfo innerClassInfo = topClassType.getInnerClassHereInfo();
        JavaTypeInstance classType = topClassType;
        while (innerClassInfo != InnerClassInfo.NOT) {
            JavaTypeInstance nextClass = innerClassInfo.getOuterClass();
            if (nextClass == null || nextClass.equals(classType)) break;
            classType = nextClass;
            innerClassInfo = classType.getInnerClassHereInfo();
        }

        Matcher<StructuredStatement> m = new ResetAfterTest(wcm1,
                new CollectMatch("ass1", new StructuredAssignment(BytecodeLoc.NONE,
                        wcm1.getStaticVariable("assertbool", topClassType, new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.TEST)),
                        new NotOperation(BytecodeLoc.NONE, new BooleanExpression(
                                wcm1.getMemberFunction("assertmeth", "desiredAssertionStatus",
                                        new Literal(TypedLiteral.getClass(classType))
                                )
                        ))
                ))
        );

        AssertVarCollector matchResultCollector = new AssertVarCollector(wcm1);
        while (mi.hasNext()) {
            mi.advance();
            matchResultCollector.clear();
            if (m.match(mi, matchResultCollector)) {
                // This really should only match once.  If it matches multiple times, something else
                // is being identically initialised, which is probably wrong!
                if (matchResultCollector.matched()) break;
                mi.rewind1();
            }
        }
        if (!matchResultCollector.matched()) return;
        assertionStatic = matchResultCollector.assertStatic;

        /*
         * Ok, now we know what the assertion field is.  Let's search all conditionals for it - if we find one,
         * we can transform that conditional into an assert
         *
         * if (!assertionsDisabledField && (x)) --> assert(!x))
         */
        rewriteMethods();

    }

    private class AssertVarCollector extends AbstractMatchResultIterator {

        private final WildcardMatch wcm;
        ClassFileField assertField = null;
        StaticVariable assertStatic = null;

        private AssertVarCollector(WildcardMatch wcm) {
            this.wcm = wcm;
        }

        @Override
        public void clear() {
            assertField = null;
            assertStatic = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            StaticVariable staticVariable = wcm.getStaticVariable("assertbool").getMatch();

            ClassFileField field;
            try {
                field = classFile.getFieldByName(staticVariable.getFieldName(), staticVariable.getInferredJavaType().getJavaTypeInstance());
            } catch (NoSuchFieldException e) {
                return;
            }
            if (!field.getField().testAccessFlag(AccessFlag.ACC_SYNTHETIC)) return;
            assertField = field;
            statement.getContainer().nopOut();
            assertField.markHidden();
            assertStatic = staticVariable;
        }

        boolean matched() {
            return (assertField != null);
        }
    }

    private void rewriteMethods() {
        List<Method> methods = classFile.getMethods();
        WildcardMatch wcm1 = new WildcardMatch();
        Matcher<StructuredStatement> standardAssertMatcher = buildStandardAssertMatcher(wcm1);
        AssertUseCollector collector = new AssertUseCollector(wcm1);

        Matcher<StructuredStatement> switchAssertMatcher = buildSwitchAssertMatcher(wcm1);
        SwitchAssertUseCollector swcollector = new SwitchAssertUseCollector();

        for (Method method : methods) {
            if (!method.hasCodeAttribute()) continue;

            Op04StructuredStatement top = method.getAnalysis();
            if (top == null) continue;

            handlePreConditionedAsserts(top);

            /*
             * Pre-transform a couple of particularly horrible samples.
             * (where the assertion gets moved into a while block test).
             * See InfiniteAssert tests.
             */
            handleInfiniteAsserts(top);


            List<StructuredStatement> statements = MiscStatementTools.linearise(top);

            if (statements == null) continue;

            MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(statements);

            while (mi.hasNext()) {
                mi.advance();
                if (standardAssertMatcher.match(mi, collector)) {
                    mi.rewind1();
                }
            }

            // If we're looking for switch expression assertions, things get more interesting.
            // We can't search for a simple pattern any more, but we can find possible entry points.
            if (switchExpressions) {
                mi = new MatchIterator<StructuredStatement>(statements);

                while (mi.hasNext()) {
                    mi.advance();
                    if (switchAssertMatcher.match(mi, swcollector)) {
                        mi.rewind1();
                    }
                }
            }
        }
    }

    private Matcher<StructuredStatement> buildSwitchAssertMatcher(WildcardMatch wcm1) {
        return new ResetAfterTest(wcm1,
            new CollectMatch("ass1", new MatchSequence(
                    new BeginBlock(null),
                    new StructuredIf(BytecodeLoc.NONE,
                            new NotOperation(BytecodeLoc.NONE, new BooleanExpression(new LValueExpression(assertionStatic))), null
                    ),
                    new BeginBlock(null),
                    new StructuredSwitch(BytecodeLoc.NONE, wcm1.getExpressionWildCard("switchExpression"), null, wcm1.getBlockIdentifier("switchblock"))
            ))
        );
    }

    private Matcher<StructuredStatement> buildStandardAssertMatcher(WildcardMatch wcm1) {
        return new ResetAfterTest(wcm1,
                    new MatchOneOf(
                            new CollectMatch("ass1", new MatchSequence(
                                    new StructuredIf(BytecodeLoc.NONE,
                                            new BooleanOperation(BytecodeLoc.NONE,
                                                    new NotOperation(BytecodeLoc.NONE, new BooleanExpression(new LValueExpression(assertionStatic))),
                                                    wcm1.getConditionalExpressionWildcard("condition"),
                                                    BoolOp.AND), null
                                    ),
                                    new BeginBlock(null),
                                    new StructuredThrow(BytecodeLoc.NONE, wcm1.getConstructorSimpleWildcard("exception", TypeConstants.ASSERTION_ERROR)),
                                    new EndBlock(null)
                            )),
                            // obviated by demorgan pass?
                            new CollectMatch("ass1b", new MatchSequence(
                                    new StructuredIf(BytecodeLoc.NONE,
                                            new NotOperation(BytecodeLoc.NONE,
                                                    new BooleanOperation(BytecodeLoc.NONE, new BooleanExpression(new LValueExpression(assertionStatic)),
                                                            wcm1.getConditionalExpressionWildcard("condition"),
                                                            BoolOp.OR)), null
                                    ),
                                    new BeginBlock(null),
                                    new StructuredThrow(BytecodeLoc.NONE, wcm1.getConstructorSimpleWildcard("exception", TypeConstants.ASSERTION_ERROR)),
                                    new EndBlock(null)
                            )),
                            new CollectMatch("ass1c", new MatchSequence(
                                    new StructuredIf(BytecodeLoc.NONE, new NotOperation(BytecodeLoc.NONE, new BooleanExpression(new LValueExpression(assertionStatic))), null ),
                                    new BeginBlock(null),
                                    new StructuredIf(BytecodeLoc.NONE, wcm1.getConditionalExpressionWildcard("condition"), null ),
                                    new BeginBlock(null),
                                    new StructuredThrow(BytecodeLoc.NONE, wcm1.getConstructorSimpleWildcard("exception", TypeConstants.ASSERTION_ERROR)),
                                    new EndBlock(null) ,
                                    new EndBlock(null)
                            )),
                            new CollectMatch("ass2", new MatchSequence(
                                    new MatchOneOf(
                                            new StructuredIf(BytecodeLoc.NONE,
                                                    new BooleanOperation(BytecodeLoc.NONE,
                                                            new BooleanExpression(new LValueExpression(assertionStatic)),
                                                            wcm1.getConditionalExpressionWildcard("condition2"),
                                                            BoolOp.OR), null),
                                            new StructuredIf(BytecodeLoc.NONE,
                                                    new BooleanExpression(new LValueExpression(assertionStatic)), null)
                                    ),
                                    new BeginBlock(wcm1.getBlockWildcard("condBlock")),
                                    new MatchOneOf(
                                            new StructuredReturn(BytecodeLoc.NONE, null, null),
                                            new StructuredReturn(BytecodeLoc.NONE, wcm1.getExpressionWildCard("retval"), null),
                                            new StructuredBreak(BytecodeLoc.NONE, wcm1.getBlockIdentifier("breakblock"), false)
                                    ),
                                    new EndBlock(wcm1.getBlockWildcard("condBlock")),
                                    new CollectMatch("ass2throw", new StructuredThrow(BytecodeLoc.NONE, wcm1.getConstructorSimpleWildcard("exception2", TypeConstants.ASSERTION_ERROR)))
                            )),
                            new CollectMatch("assonly", new MatchSequence(
                                    new StructuredIf(BytecodeLoc.NONE,
                                            new NotOperation(BytecodeLoc.NONE, new BooleanExpression(new LValueExpression(assertionStatic))), null
                                    ),
                                    new BeginBlock(null),
                                    new StructuredThrow(BytecodeLoc.NONE, wcm1.getConstructorSimpleWildcard("exception", TypeConstants.ASSERTION_ERROR)),
                                    new EndBlock(null)
                            ))
                    )
            );
    }

    /*
     * Consider AssertTest19:
     *  public static void f(Integer x) {
     *   if (x > 1) {
     *       assert (x!=3);
     *   }
     * }
     *
     * The first test happens regardless, the second inside the assert
     *
     * reasonable : if (x>1&& !assertionsDisabled && x==3) throw();
     * totally wrong : assert(x>1 && x!=3)
     *
     * but correct is as above ;)
     */
    private void handlePreConditionedAsserts(Op04StructuredStatement statements) {
        PreconditionAssertRewriter rewriter = new PreconditionAssertRewriter(assertionStatic);
        rewriter.transform(statements);
    }

    private void handleInfiniteAsserts(Op04StructuredStatement statements) {
        InfiniteAssertRewriter rewriter = new InfiniteAssertRewriter(assertionStatic);
        rewriter.transform(statements);
    }

    private class SwitchAssertUseCollector extends AbstractMatchResultIterator {

        private SwitchAssertUseCollector() {
        }

        @Override
        public void clear() {
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            /* We expect this statement to be a block, containing our test, and starting a switch.
             * We don't know if the switch is the only statement in the block (exception has been rolled up)
             * or if there's ONE throw after it.
             *
             * If neither, bail!
             */
            if (!(statement instanceof BeginBlock)) return;

            Block block = ((BeginBlock)statement).getBlock();
            Pair<Boolean, Op04StructuredStatement> content = block.getOneStatementIfPresent();
            if (content.getFirst()) return;

            StructuredStatement ifStm = content.getSecond().getStatement();
            if (!(ifStm instanceof StructuredIf)) return;

            Op04StructuredStatement taken = ((StructuredIf) ifStm).getIfTaken();
            StructuredStatement takenBody = taken.getStatement();
            if (takenBody.getClass() != Block.class) return;
            Block takenBlock = (Block)takenBody;
            // This will either have a switch ONLY, or a switch and a throw.
            List<Op04StructuredStatement> switchAndThrow = takenBlock.getFilteredBlockStatements();
            if (switchAndThrow.isEmpty()) return;

            BlockIdentifier outerBlock = block.getBreakableBlockOrNull();

            StructuredStatement switchS = switchAndThrow.get(0).getStatement();
            if (!(switchS instanceof StructuredSwitch)) return;
            StructuredSwitch struSwi = (StructuredSwitch)switchS;
            BlockIdentifier switchBlock = struSwi.getBlockIdentifier();
            Op04StructuredStatement swBody = struSwi.getBody();
            if (!(swBody.getStatement() instanceof Block)) {
                return;
            }
            Block swBodyBlock = (Block)(swBody.getStatement());

            if (switchAndThrow.size() > 2) {
                // It's possible that this is a switch with no content, because we've extracted all the content
                // after the default.
                // if so, we could aggressively roll it up, and try that.
                // (test SwitchExpressionAssert1d)
                switchAndThrow = tryCombineSwitch(switchAndThrow, outerBlock, switchBlock, swBodyBlock);
                if (switchAndThrow.size() != 1) return;
                takenBlock.replaceBlockStatements(switchAndThrow);
            }
            StructuredStatement newAssert = null;


            switch (switchAndThrow.size()) {
                case 1:
                    // switch, with throw rolled up into last case.
                    newAssert = processSwitchEmbeddedThrow(ifStm, outerBlock, swBodyBlock, swBody, struSwi);
                    break;
                case 2:
                    // switch, followed by throw.
                    // In this version, if it ends up leaving the if, it's true.
                    // if it ends up leaving the switch (so into the throw), it's false.
                    newAssert = processSwitchAndThrow(ifStm, outerBlock, switchBlock, swBodyBlock, struSwi, switchAndThrow.get(1));
                    break;
                default:
                    // switch, with logic dropping off the last case.
                    break;
            }
            if (newAssert != null) {
                content.getSecond().replaceStatement(newAssert);
            }
        }

        // We know the first statement is a switch - if it doesn't have any breaks in it, then we're safe to
        // roll outer statements in.
        private List<Op04StructuredStatement> tryCombineSwitch(List<Op04StructuredStatement> content, BlockIdentifier outer, BlockIdentifier swiBlockIdentifier, Block swBodyBlock) {
            Map<Op04StructuredStatement, StructuredExpressionYield> replacements = MapFactory.newOrderedMap();
            ControlFlowSwitchExpressionTransformer cfset = new ControlFlowSwitchExpressionTransformer(outer, swiBlockIdentifier, replacements);
            content.get(0).transform(cfset, new StructuredScope());

            if (cfset.failed) return content;
            if (cfset.falseFound != 0) return content;

            List<Op04StructuredStatement> cases = swBodyBlock.getFilteredBlockStatements();
            Op04StructuredStatement lastCase = cases.get(cases.size()-1);
            StructuredStatement ss = lastCase.getStatement();
            if (!(ss instanceof StructuredCase)) return content;
            Op04StructuredStatement body = ((StructuredCase) ss).getBody();
            StructuredStatement bodySS = body.getStatement();
            Block block;
            if (bodySS instanceof Block) {
                block = (Block)bodySS;
            } else {
                block = new Block(body);
            }
            block.setIndenting(true);
            block.getBlockStatements().addAll(content.subList(1, content.size()));
            body.replaceStatement(block);
            return ListFactory.newList(content.get(0));
        }

        private Pair<Boolean, Expression> getThrowExpression(StructuredStatement throwS) {
            WildcardMatch wcm2 = new WildcardMatch();
            WildcardMatch.ConstructorInvokationSimpleWildcard constructor = wcm2.getConstructorSimpleWildcard("exception", TypeConstants.ASSERTION_ERROR);
            StructuredStatement test = new StructuredThrow(BytecodeLoc.TODO, constructor);
            if (!test.equals(throwS)) return Pair.make(false, null);;
            Expression exceptArg = null;
            List<Expression> consArg = constructor.getMatch().getArgs();
            if (consArg.size() == 1) {
                exceptArg = consArg.get(0);
            } else if (consArg.size() > 1) {
                return Pair.make(false, null);
            }
            return Pair.make(true, exceptArg);
        }

        private StructuredStatement processSwitchAndThrow(StructuredStatement ifStm, BlockIdentifier outer, BlockIdentifier swiBlockIdentifier, Block swBodyBlock, StructuredSwitch struSwi, Op04StructuredStatement throwStm) {
            // First, we need to verify that the throw expression really is one
            Pair<Boolean, Expression> excepTest = getThrowExpression(throwStm.getStatement());
            if (!excepTest.getFirst()) return null;
            Expression exceptArg = excepTest.getSecond();

            // The switch should have only BREAK immediate, BREAK outer, or THROW exits.
            // break immediate -> yield false
            // break outer -> yield true
            // However, the switch could itself have complex content in it.
            List<SwitchExpression.Branch> branches = ListFactory.newList();
            Map<Op04StructuredStatement, StructuredExpressionYield> replacements = MapFactory.newOrderedMap();
            if (!getBranches(outer, swiBlockIdentifier, swBodyBlock, branches, replacements, false)) return null;

            SwitchExpression sw = new SwitchExpression(BytecodeLoc.TODO, boolIjt, struSwi.getSwitchOn(), branches);
            return ((StructuredIf)ifStm).convertToAssertion(StructuredAssert.mkStructuredAssert(BytecodeLoc.TODO, new BooleanExpression(sw), exceptArg));
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private boolean getBranches(BlockIdentifier outer, BlockIdentifier swiBlockIdentifier, Block swBodyBlock, List<SwitchExpression.Branch> branches, Map<Op04StructuredStatement, StructuredExpressionYield> replacements, boolean addYieldTrue) {
            for (Op04StructuredStatement statement :  swBodyBlock.getBlockStatements()) {
                SwitchExpression.Branch branch = getBranch(outer, swiBlockIdentifier, replacements, statement, addYieldTrue);
                if (branch == null) return false;
                branches.add(branch);
            }
            for (Map.Entry<Op04StructuredStatement, StructuredExpressionYield> replacement : replacements.entrySet()) {
                Op04StructuredStatement first = replacement.getKey();
                StructuredStatement statement = first.getStatement();
                if (statement instanceof StructuredBreak) {
                    StructuredBreak sb = (StructuredBreak) statement;
                    if (!sb.isLocalBreak()) sb.getBreakBlock().releaseForeignRef();
                }
                first.replaceStatement(replacement.getValue());
            }
            return true;
        }

        private SwitchExpression.Branch getBranch(BlockIdentifier outer, BlockIdentifier swiBlockIdentifier, Map<Op04StructuredStatement, StructuredExpressionYield> replacements, Op04StructuredStatement statement, boolean addYieldTrue) {
            StructuredStatement cstm = statement.getStatement();
            if (!(cstm instanceof StructuredCase)) return null;
            StructuredCase caseStm = (StructuredCase)cstm;
            ControlFlowSwitchExpressionTransformer cfset = new ControlFlowSwitchExpressionTransformer(outer, swiBlockIdentifier, replacements);
            Op04StructuredStatement body = caseStm.getBody();
            body.transform(cfset, new StructuredScope());
            if (cfset.failed) return null;
            if (addYieldTrue) {
                StructuredStatement stm = body.getStatement();
                if (stm instanceof Block) {
                    Block block = (Block) stm;
                    Op04StructuredStatement last = block.getLast();
                    StructuredStatement lastStm = replacements.get(last);
                    if (lastStm == null) {
                        lastStm = last.getStatement();
                    }

                    if (!(lastStm instanceof StructuredExpressionYield)) {
                        cfset.totalStatements++;
                        block.getBlockStatements().add(new Op04StructuredStatement(new StructuredExpressionYield(BytecodeLoc.TODO, Literal.TRUE)));
                    }
                }
            }
            Expression value =
                cfset.totalStatements == 0 ?
                cfset.single :
                new StructuredStatementExpression(boolIjt, body.getStatement());
            return new SwitchExpression.Branch(caseStm.getValues(), value);
        }

        private StructuredStatement processSwitchEmbeddedThrow(StructuredStatement ifStm, BlockIdentifier outer, Block swBodyBlock, Op04StructuredStatement switchStm, StructuredSwitch struSwi) {
            // Soooo - if there's only ONE Assertion error that gets thrown, we can be pretty confident that
            // that's the assertion.
            // If there are multiple?
            // If they're identical, then we COULD consider them the same.  If not, give up, go home.
            // Also, don't forget to add a 'yield true' at the end of the last block, if there's not a yield or a throw there already!
            Map<Op04StructuredStatement, StructuredExpressionYield> replacements = MapFactory.newOrderedMap();
            BlockIdentifier swiBlockIdentifier = struSwi.getBlockIdentifier();
            AssertionTrackingControlFlowSwitchExpressionTransformer track = new AssertionTrackingControlFlowSwitchExpressionTransformer(swiBlockIdentifier, outer, replacements);
            switchStm.transform(track, new StructuredScope());
            if (track.failed) return null;
            if (track.throwSS.size() > 1) {
                return null;
            }

            replacements.clear();
            Expression exceptArg = null;
            if (track.throwSS.size() == 1) {
                StructuredStatement throwStm = track.throwSS.get(0);
                Pair<Boolean, Expression> excepTest = getThrowExpression(throwStm);
                if (!excepTest.getFirst()) return null;
                exceptArg = excepTest.getSecond();
                replacements.put(throwStm.getContainer(), new StructuredExpressionYield(BytecodeLoc.TODO, Literal.FALSE));
            }

            List<SwitchExpression.Branch> branches = ListFactory.newList();
            if (!getBranches(swiBlockIdentifier, swiBlockIdentifier, swBodyBlock, branches, replacements, true)) return null;
            // And add yield true to the end of every branch that could roll off.

            SwitchExpression sw = new SwitchExpression(BytecodeLoc.TODO, boolIjt, struSwi.getSwitchOn(), branches);
            return ((StructuredIf)ifStm).convertToAssertion(StructuredAssert.mkStructuredAssert(BytecodeLoc.TODO, new BooleanExpression(sw), exceptArg));

        }
    }

    static class AssertionTrackingControlFlowSwitchExpressionTransformer extends ControlFlowSwitchExpressionTransformer {
        List<StructuredStatement> throwSS = ListFactory.newList();

        AssertionTrackingControlFlowSwitchExpressionTransformer(BlockIdentifier trueBlock, BlockIdentifier falseBlock, Map<Op04StructuredStatement, StructuredExpressionYield> replacements) {
            super(trueBlock, falseBlock, replacements);
        }

        @Override
        void additionalHandling(StructuredStatement in) {
            if (in instanceof StructuredThrow) {
                if (((StructuredThrow) in).getValue().getInferredJavaType().getJavaTypeInstance().equals(TypeConstants.ASSERTION_ERROR)) {
                    throwSS.add(in);
                }
            }
        }
    }

    static class ControlFlowSwitchExpressionTransformer implements StructuredStatementTransformer {
        private Map<Op04StructuredStatement, StructuredExpressionYield> replacements;
        protected boolean failed;
        int totalStatements;
        Expression single;
        int trueFound = 0;
        int falseFound = 0;
        private BlockIdentifier trueBlock;
        private BlockIdentifier falseBlock;

        private ControlFlowSwitchExpressionTransformer(BlockIdentifier trueBlock, BlockIdentifier falseBlock, Map<Op04StructuredStatement, StructuredExpressionYield> replacements) {
            this.trueBlock = trueBlock;
            this.falseBlock = falseBlock;
            this.replacements = replacements;
        }

        void additionalHandling(StructuredStatement in) {
        }

        @Override
        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
            if (failed) return in;

            if (in.isEffectivelyNOP()) return in;

            if (!(in instanceof Block)) {
                StructuredExpressionYield y = (StructuredExpressionYield)replacements.get(in.getContainer());
                if (y == null) {
                    totalStatements++;
                } else {
                    single = y.getValue();
                }
            }

            if (in instanceof StructuredBreak) {
                BreakClassification bk = classifyBreak((StructuredBreak)in, scope);
                switch (bk) {
                    case TRUE_BLOCK:
                        totalStatements--;
                        trueFound++;
                        single = Literal.TRUE;
                        replacements.put(in.getContainer(), new StructuredExpressionYield(BytecodeLoc.NONE, single));
                        return in;
                    case FALSE_BLOCK:
                        totalStatements--;
                        falseFound++;
                        single = Literal.FALSE;
                        replacements.put(in.getContainer(), new StructuredExpressionYield(BytecodeLoc.NONE, single));
                        return in;
                    case INNER:
                        break;
                    case TOO_FAR:
                        failed = true;
                        return in;
                }
            }

            if (in instanceof StructuredReturn) {
                failed = true;
                return in;
            }

            additionalHandling(in);

            in.transformStructuredChildren(this, scope);
            return in;
        }

        BreakClassification classifyBreak(StructuredBreak in, StructuredScope scope) {
            BlockIdentifier breakBlock = in.getBreakBlock();
            if (breakBlock == trueBlock) return BreakClassification.TRUE_BLOCK;
            if (breakBlock == falseBlock) return BreakClassification.FALSE_BLOCK;
            for (StructuredStatement stm : scope.getAll()) {
                BlockIdentifier block = stm.getBreakableBlockOrNull();
                if (block == breakBlock) return BreakClassification.INNER;
            }
            return BreakClassification.TOO_FAR;
        }

        enum BreakClassification {
            TRUE_BLOCK,
            FALSE_BLOCK,
            TOO_FAR,
            INNER
        }
    }


    private class AssertUseCollector extends AbstractMatchResultIterator {

        private StructuredStatement ass2throw;

        private final WildcardMatch wcm;

        private AssertUseCollector(WildcardMatch wcm) {
            this.wcm = wcm;
        }

        @Override
        public void clear() {
            ass2throw = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            WildcardMatch.ConstructorInvokationSimpleWildcard constructor = wcm.getConstructorSimpleWildcard("exception");
            List<Expression> args = constructor.getMatch().getArgs();
            Expression arg = args.size() > 0 ? args.get(0) : null;
            if (arg != null) {
                // We can remove a spurious cast to Object.
                if (arg instanceof CastExpression && arg.getInferredJavaType().getJavaTypeInstance() == TypeConstants.OBJECT) {
                    arg = ((CastExpression) arg).getChild();
                }
            }
            if (name.equals("ass1") || name.equals("ass1b") || name.equals("ass1c")) {
                StructuredIf ifStatement = (StructuredIf) statement;
                ConditionalExpression condition = wcm.getConditionalExpressionWildcard("condition").getMatch();
                if (name.equals("ass1") || name.equals("ass1c")) condition = new NotOperation(BytecodeLoc.TODO, condition);
                condition = condition.simplify();
                StructuredStatement structuredAssert = ifStatement.convertToAssertion(StructuredAssert.mkStructuredAssert(BytecodeLoc.TODO, condition,arg));
                ifStatement.getContainer().replaceStatement(structuredAssert);
            } else if (name.equals("ass2")) {
                if (ass2throw == null) throw new IllegalStateException();
                StructuredIf ifStatement = (StructuredIf) statement;
                // If there's a condition, it's in condition 2, otherwise it's an assert literal.
                WildcardMatch.ConditionalExpressionWildcard wcard = wcm.getConditionalExpressionWildcard("condition2");
                ConditionalExpression conditionalExpression = wcard.getMatch();
                if (conditionalExpression == null)
                    conditionalExpression = new BooleanExpression(new Literal(TypedLiteral.getBoolean(0)));
                // The if statement becomes an assert conditjon, the throw statement becomes the content of the if block.
                StructuredStatement structuredAssert = StructuredAssert.mkStructuredAssert(BytecodeLoc.TODO, conditionalExpression,arg);
                ifStatement.getContainer().replaceStatement(structuredAssert);
                ass2throw.getContainer().replaceStatement(ifStatement.getIfTaken().getStatement());
            } else if (name.equals("ass2throw")) {
                ass2throw = statement;
            } else if (name.equals("assonly")) {
                StructuredIf ifStatement = (StructuredIf) statement;
                StructuredStatement structuredAssert = ifStatement.convertToAssertion(StructuredAssert.mkStructuredAssert(BytecodeLoc.TODO, new BooleanExpression(Literal.FALSE),arg));
                ifStatement.getContainer().replaceStatement(structuredAssert);
            }
        }
    }

}
