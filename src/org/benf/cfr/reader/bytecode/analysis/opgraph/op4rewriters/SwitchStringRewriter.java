package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.BytecodeMeta;
import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.AbstractMatchResultIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.CollectMatch;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.KleenePlus;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.KleeneStar;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchOneOf;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchSequence;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.Matcher;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.ResetAfterTest;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NotOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredBreak;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredCase;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredIf;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredSwitch;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SwitchStringRewriter implements Op04Rewriter {
    private final Options options;
    private final ClassFileVersion classFileVersion;
    private final BytecodeMeta bytecodeMeta;

    public SwitchStringRewriter(Options options, ClassFileVersion classFileVersion, BytecodeMeta bytecodeMeta) {
        this.options = options;
        this.classFileVersion = classFileVersion;
        this.bytecodeMeta = bytecodeMeta;
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        if (!(options.getOption(OptionsImpl.STRING_SWITCH, classFileVersion)
                || bytecodeMeta.has(BytecodeMeta.CodeInfoFlag.STRING_SWITCHES))
        ) return;

        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        rewriteComplex(structuredStatements);
        rewriteEmpty(structuredStatements);
    }

    /*
     * also handle the (bizarre) situation where
     *
     * switch (foo) {
     *    default:
     *    --->
     * }
     *
     * gives us
     *
     * foo.hashcode()
     * switch(-1) {
     *    default:
     * }
     *
     * Note that this doesn't pull anything into the switch (so switch expressions will need further work).
     */
    private void rewriteEmpty(List<StructuredStatement> structuredStatements) {
        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        WildcardMatch wcm = new WildcardMatch();

        Matcher<StructuredStatement> m = new ResetAfterTest(wcm, "r1", new MatchSequence(
                new CollectMatch("ass1", new StructuredAssignment(BytecodeLoc.NONE, wcm.getLValueWildCard("stringobject"), wcm.getExpressionWildCard("originalstring"))),
                new CollectMatch("ass2", new StructuredAssignment(BytecodeLoc.NONE, wcm.getLValueWildCard("intermed"), Literal.MINUS_ONE)),
                new CollectMatch("hash", new StructuredExpressionStatement(BytecodeLoc.NONE,
                        wcm.getMemberFunction("hash", MiscConstants.HASHCODE, wcm.getExpressionWildCard("stringobjornull")), false
                )),
                new CollectMatch("switch1",
                        new StructuredSwitch(BytecodeLoc.NONE, new LValueExpression(wcm.getLValueWildCard("intermed")),
                                null,
                                wcm.getBlockIdentifier("switchblock"))),
                new BeginBlock(null),
                new StructuredCase(BytecodeLoc.NONE, Collections.<Expression>emptyList(), null, null, wcm.getBlockIdentifier("case")),
                new BeginBlock(null),
                new EndBlock(null),
                new EndBlock(null)
        ));

        EmptySwitchStringMatchResultCollector matchResultCollector = new EmptySwitchStringMatchResultCollector(wcm);
        while (mi.hasNext()) {
            mi.advance();
            matchResultCollector.clear();
            if (m.match(mi, matchResultCollector)) {
                if (!isLVOk(matchResultCollector.verify, matchResultCollector.lvalue, matchResultCollector.getStatementByName("ass1"))) continue;
                StructuredSwitch swtch = (StructuredSwitch) matchResultCollector.getStatementByName("switch1");
                if (!swtch.isSafeExpression()) continue;

                matchResultCollector.getStatementByName("ass1").getContainer().nopOut();
                matchResultCollector.getStatementByName("ass2").getContainer().nopOut();
                matchResultCollector.getStatementByName("hash").getContainer().nopOut();

                Op04StructuredStatement body = swtch.getBody();
                swtch.getContainer().replaceStatement(new StructuredSwitch(BytecodeLoc.TODO, matchResultCollector.string, body, swtch.getBlockIdentifier()));
                mi.rewind1();
            }
        }

    }

    private void rewriteComplex(List<StructuredStatement> structuredStatements) {
        // Rather than have a non-greedy kleene star at the start, we cheat and scan for valid start points.
        // switch OB (case OB (if-testalternativevalid OB assign break CB)* if-notvalid break assign break CB)+ CB
        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        WildcardMatch wcm1 = new WildcardMatch();
        WildcardMatch wcm2 = new WildcardMatch();
        WildcardMatch wcm3 = new WildcardMatch();

        Matcher<StructuredStatement> m = new ResetAfterTest(wcm1, "r1", new MatchSequence(
                new CollectMatch("ass1", new StructuredAssignment(BytecodeLoc.NONE, wcm1.getLValueWildCard("stringobject"), wcm1.getExpressionWildCard("originalstring"))),
                new CollectMatch("ass2", new StructuredAssignment(BytecodeLoc.NONE, wcm1.getLValueWildCard("intermed"), wcm1.getExpressionWildCard("defaultintermed"))),
                new CollectMatch("switch1",
                        new StructuredSwitch(BytecodeLoc.NONE, wcm1.getMemberFunction("switch", "hashCode", wcm1.getExpressionWildCard("stringobjornull")),
                                null,
                                wcm1.getBlockIdentifier("switchblock"))),
                new BeginBlock(null),
                new KleenePlus(
                        new ResetAfterTest(wcm2, "r2",
                                new MatchSequence(
                                        new StructuredCase(BytecodeLoc.NONE, wcm2.getList("hashvals"), null, null, wcm2.getBlockIdentifier("case")),
                                        new BeginBlock(null),
                                        new KleeneStar(
                                                new ResetAfterTest(wcm3,"r3",
                                                        new MatchSequence(
                                                                new StructuredIf(BytecodeLoc.NONE, new BooleanExpression(wcm3.getMemberFunction("collision", "equals", new LValueExpression(wcm1.getLValueWildCard("stringobject")), wcm3.getExpressionWildCard("stringvalue"))), null),
                                                                new BeginBlock(null),
                                                                new StructuredAssignment(BytecodeLoc.NONE, wcm1.getLValueWildCard("intermed"), wcm3.getExpressionWildCard("case2id")),
                                                                new StructuredBreak(BytecodeLoc.NONE, wcm1.getBlockIdentifier("switchblock"), true),
                                                                new EndBlock(null)
                                                        )
                                                )
                                        ),
                                        new MatchOneOf(
                                                // Either an anticollision at the end
                                                new MatchSequence(
                                                    new StructuredIf(BytecodeLoc.NONE, new NotOperation(BytecodeLoc.NONE, new BooleanExpression(wcm2.getMemberFunction("anticollision", "equals", new LValueExpression(wcm1.getLValueWildCard("stringobject")), wcm2.getExpressionWildCard("stringvalue")))), null),
                                                    new StructuredBreak(BytecodeLoc.NONE, wcm1.getBlockIdentifier("switchblock"), true),
                                                    new StructuredAssignment(BytecodeLoc.NONE, wcm1.getLValueWildCard("intermed"), wcm2.getExpressionWildCard("case2id"))
                                                ),
                                                // or a final collision.
                                                new MatchSequence(
                                                        new StructuredIf(BytecodeLoc.NONE, new BooleanExpression(wcm2.getMemberFunction("collision", "equals", new LValueExpression(wcm1.getLValueWildCard("stringobject")), wcm2.getExpressionWildCard("stringvalue"))), null),
                                                        new BeginBlock(null),
                                                        new StructuredAssignment(BytecodeLoc.NONE, wcm1.getLValueWildCard("intermed"), wcm2.getExpressionWildCard("case2id")),
                                                        new EndBlock(null)
                                                )
                                        ),
                                        // Strictly speaking wrong, but I want to capture a missing break at the end.
                                        new KleeneStar(new StructuredBreak(BytecodeLoc.NONE, wcm1.getBlockIdentifier("switchblock"), true)),
                                        new EndBlock(null)
                                )
                        )
                ),
                new EndBlock(null),
                // We don't actually CARE what the branches of the switch-on-intermediate are...
                // we just want to make sure that there is one.
                new CollectMatch("switch2", new StructuredSwitch(BytecodeLoc.NONE, new LValueExpression(wcm1.getLValueWildCard("intermed")), null, wcm1.getBlockIdentifier("switchblock2")))
        ));

        SwitchStringMatchResultCollector matchResultCollector = new SwitchStringMatchResultCollector(wcm1, wcm2, wcm3);
        while (mi.hasNext()) {
            mi.advance();
            matchResultCollector.clear();
            if (m.match(mi, matchResultCollector)) {
                if (!isLVOk(matchResultCollector.verify, matchResultCollector.lvalue, matchResultCollector.getStatementByName("ass1"))) continue;
                StructuredSwitch firstSwitch = (StructuredSwitch) matchResultCollector.getStatementByName("switch1");
                StructuredSwitch secondSwitch = (StructuredSwitch) matchResultCollector.getStatementByName("switch2");
                if (!secondSwitch.isSafeExpression()) continue;

                StructuredSwitch replacement = rewriteSwitch(secondSwitch, matchResultCollector);
                secondSwitch.getContainer().replaceStatement(replacement);
                firstSwitch.getContainer().nopOut();
                matchResultCollector.getStatementByName("ass1").getContainer().nopOut();
                matchResultCollector.getStatementByName("ass2").getContainer().nopOut();
                mi.rewind1();
            }
        }
    }

    private StructuredSwitch rewriteSwitch(StructuredSwitch original, SwitchStringMatchResultCollector matchResultCollector) {
        Op04StructuredStatement body = original.getBody();
        BlockIdentifier blockIdentifier = original.getBlockIdentifier();

        StructuredStatement inner = body.getStatement();
        if (!(inner instanceof Block)) {
            throw new FailedRewriteException("Switch body is not a block, is a " + inner.getClass());
        }

        Block block = (Block) inner;

        Map<Integer, List<String>> replacements = matchResultCollector.getValidatedHashes();
        List<Op04StructuredStatement> caseStatements = block.getBlockStatements();
        LinkedList<Op04StructuredStatement> tgt = ListFactory.newLinkedList();

        InferredJavaType typeOfSwitch = matchResultCollector.getStringExpression().getInferredJavaType();
        for (Op04StructuredStatement op04StructuredStatement : caseStatements) {
            inner = op04StructuredStatement.getStatement();
            if (!(inner instanceof StructuredCase)) {
                throw new FailedRewriteException("Block member is not a case, it's a " + inner.getClass());
            }
            StructuredCase structuredCase = (StructuredCase) inner;
            List<Expression> values = structuredCase.getValues();
            List<Expression> transformedValues = ListFactory.newList();

            for (Expression value : values) {
                Integer i = getInt(value);
                List<String> replacementStrings = replacements.get(i);
                if (replacementStrings == null) {
                    throw new FailedRewriteException("No replacements for " + i);
                }
                for (String s : replacementStrings) {
                    transformedValues.add(new Literal(TypedLiteral.getString(s)));
                }
            }

            StructuredCase replacementStructuredCase = new StructuredCase(BytecodeLoc.TODO, transformedValues, typeOfSwitch, structuredCase.getBody(), structuredCase.getBlockIdentifier());
            tgt.add(new Op04StructuredStatement(replacementStructuredCase));
        }
        Block newBlock = new Block(tgt, true);

        Expression switchOn = matchResultCollector.getStringExpression();

        // If the literal is a naughty null, we need to expressly force it to a string.
        // Don't cast to its own type, as this might be a null type.
        if (switchOn.equals(Literal.NULL)) {
            switchOn = new CastExpression(BytecodeLoc.TODO, new InferredJavaType(TypeConstants.STRING, InferredJavaType.Source.EXPRESSION), switchOn, true);
        }

        return new StructuredSwitch(
                BytecodeLoc.TODO,
                switchOn,
                new Op04StructuredStatement(newBlock),
                blockIdentifier, false);
    }

    private static boolean isLVOk(Expression lve, LValue lv, StructuredStatement assign) {
        if (lve instanceof LValueExpression && ((LValueExpression) lve).getLValue().equals(lv)) return true;
        if (!(lve instanceof Literal)) return false;
        if (!(assign instanceof StructuredAssignment)) return false;
        Expression rv = ((StructuredAssignment) assign).getRvalue();
        return rv.equals(lve);
    }

    private static class EmptySwitchStringMatchResultCollector extends AbstractMatchResultIterator {
        private final WildcardMatch wcm;
        private Expression string;
        private final Map<String, StructuredStatement> collectedStatements = MapFactory.newMap();
        private Expression verify;
        private LValue lvalue;

        EmptySwitchStringMatchResultCollector(WildcardMatch wcm) {
            this.wcm = wcm;
        }

        @Override
        public void clear() {
            collectedStatements.clear();
            string = null;
            verify = null;
            lvalue = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            collectedStatements.put(name, statement);
        }

        @Override
        public void collectMatches(String name, WildcardMatch wcm) {
            string = wcm.getExpressionWildCard("originalstring").getMatch();
            verify = wcm.getExpressionWildCard("stringobjornull").getMatch();
            lvalue = wcm.getLValueWildCard("stringobject").getMatch();
        }

        StructuredStatement getStatementByName(String name) {
            StructuredStatement structuredStatement = collectedStatements.get(name);
            if (structuredStatement == null) throw new IllegalArgumentException("No collected statement " + name);
            return structuredStatement;
        }
    }

    private static class SwitchStringMatchResultCollector extends AbstractMatchResultIterator {

        private final WildcardMatch wholeBlock;
        private final WildcardMatch caseStatement;
        private final WildcardMatch hashCollision; // inner collision protection

        private Expression stringExpression = null;
        private final List<Pair<String, Integer>> pendingHashCode = ListFactory.newList();
        private final Map<Integer, List<String>> validatedHashes = MapFactory.newLazyMap(new UnaryFunction<Integer, List<String>>() {
            @Override
            public List<String> invoke(Integer arg) {
                return ListFactory.newList();
            }
        });
        private final Map<String, StructuredStatement> collectedStatements = MapFactory.newMap();
        private Expression verify;
        private LValue lvalue;


        private SwitchStringMatchResultCollector(WildcardMatch wholeBlock, WildcardMatch caseStatement, WildcardMatch hashCollision) {
            this.wholeBlock = wholeBlock;
            this.caseStatement = caseStatement;
            this.hashCollision = hashCollision;
        }

        @Override
        public void clear() {
            stringExpression = null;
            pendingHashCode.clear();
            validatedHashes.clear();
            collectedStatements.clear();
            verify = null;
            lvalue = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            collectedStatements.put(name, statement);
        }

        @Override
        public void collectMatches(String name, WildcardMatch wcm) {
            if (wcm == wholeBlock) {
                stringExpression = wcm.getExpressionWildCard("originalstring").getMatch();
                verify = wcm.getExpressionWildCard("stringobjornull").getMatch();
                lvalue = wcm.getLValueWildCard("stringobject").getMatch();
            } else if (wcm == caseStatement) {
                Expression case2id = wcm.getExpressionWildCard("case2id").getMatch();
                Expression stringValue = wcm.getExpressionWildCard("stringvalue").getMatch();
                pendingHashCode.add(Pair.make(getString(stringValue), getInt(case2id)));
                processPendingWithHashCode();
            } else if (wcm == hashCollision) {
                // Note that this will be triggered BEFORE the case statement it's in.
                Expression case2id = wcm.getExpressionWildCard("case2id").getMatch();
                Expression stringValue = wcm.getExpressionWildCard("stringvalue").getMatch();
                pendingHashCode.add(Pair.make(getString(stringValue), getInt(case2id)));
            } else {
                throw new IllegalStateException();
            }
        }

        void processPendingWithHashCode() {
            for (Pair<String, Integer> pair : pendingHashCode) {
                validatedHashes.get(pair.getSecond()).add(pair.getFirst());
            }
            pendingHashCode.clear();
        }

        Expression getStringExpression() {
            return stringExpression;
        }

        Map<Integer, List<String>> getValidatedHashes() {
            return validatedHashes;
        }

        StructuredStatement getStatementByName(String name) {
            StructuredStatement structuredStatement = collectedStatements.get(name);
            if (structuredStatement == null) throw new IllegalArgumentException("No collected statement " + name);
            return structuredStatement;
        }
    }

    private static String getString(Expression e) {
        if (!(e instanceof Literal)) {
            throw new TooOptimisticMatchException();
        }
        Literal l = (Literal) e;
        TypedLiteral typedLiteral = l.getValue();
        if (typedLiteral.getType() != TypedLiteral.LiteralType.String) {
            throw new TooOptimisticMatchException();
        }
        return (String) typedLiteral.getValue();
    }

    // TODO : Verify type
    private static Integer getInt(Expression e) {
        if (!(e instanceof Literal)) {
            throw new TooOptimisticMatchException();
        }
        Literal l = (Literal) e;
        TypedLiteral typedLiteral = l.getValue();
        if (typedLiteral.getType() != TypedLiteral.LiteralType.Integer) {
            throw new TooOptimisticMatchException();
        }
        return (Integer) typedLiteral.getValue();
    }

    private static class TooOptimisticMatchException extends IllegalStateException {
    }

    private static class FailedRewriteException extends IllegalStateException {
        FailedRewriteException(String s) {
            super(s);
        }
    }

}
