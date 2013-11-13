package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.iterrewriter;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.Op04Rewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.JavaArrayTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 07/02/2013
 * Time: 05:49
 */
public class LoopIterRewriter implements Op04Rewriter {
    private final Options options;
    private final ClassFileVersion classFileVersion;

    public LoopIterRewriter(Options options, ClassFileVersion classFileVersion) {
        this.options = options;
        this.classFileVersion = classFileVersion;
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        if (!options.getBooleanOpt(OptionsImpl.ARRAY_ITERATOR, classFileVersion)) return;

        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        WildcardMatch wcm = new WildcardMatch();

        LValue array$_lv = wcm.getLValueWildCard("arr$", new Predicate<LValue>() {
            @Override
            public boolean test(LValue in) {
                JavaTypeInstance type = in.getInferredJavaType().getJavaTypeInstance();
                if (type instanceof JavaArrayTypeInstance) return true;
                return false;
            }
        });

        Expression collection = wcm.getExpressionWildCard("collection");

        LValue i$_lv = wcm.getLValueWildCard("i$");
        Expression i$ = new LValueExpression(i$_lv);
        LValue iter_lv = wcm.getLValueWildCard("iter");
        Expression iter = new LValueExpression(i$_lv);


        Matcher<StructuredStatement> m = new ResetAfterTest(wcm,
                new MatchSequence(
                        new CollectMatch("i$", new StructuredAssignment(i$_lv, wcm.getMemberFunction("iterfn", "iterator", collection))),
                        new MatchOneOf(
                                new MatchSequence(
                                        new CollectMatch("do", new StructuredDo(null, null, wcm.getBlockIdentifier("doblockident"))),
                                        new BeginBlock(wcm.getBlockWildcard("doblock")),
                                        new CollectMatch("exit", new StructuredIf(new NotOperation(new BooleanExpression(wcm.getMemberFunction("hasnext", "hasNext", i$))), null)),
                                        new BeginBlock(null),
                                        new CollectMatch("exitinner",
                                                new MatchOneOf(
                                                        new StructuredBreak(wcm.getBlockIdentifier("doblockident"), false),
                                                        new StructuredReturn(wcm.getExpressionWildCard("returnvalue"), null),
                                                        new StructuredReturn(null, null)
                                                )),
                                        new EndBlock(null),
                                        new CollectMatch("incr", new StructuredAssignment(iter_lv, wcm.getMemberFunction("getnext", "next", i$))),
                                        new CollectMatchRange("body", new KleenePlus(new Negated(
                                                new EndBlock(wcm.getBlockWildcard("doblock")))
                                        )),
                                        new EndBlock(wcm.getBlockWildcard("doblock"))
                                ),
                                new MatchSequence(
                                        new CollectMatch("while", new StructuredWhile(new BooleanExpression(wcm.getMemberFunction("hasnext", "hasNext", i$)), null, wcm.getBlockIdentifier("whileblockident"))),
                                        new BeginBlock(wcm.getBlockWildcard("whileblockident")),
                                        new CollectMatch("incr", new StructuredAssignment(iter_lv, wcm.getMemberFunction("getnext", "next", i$))),
                                        new CollectMatchRange("body", new KleenePlus(new Negated(
                                                new EndBlock(wcm.getBlockWildcard("whileblockident")))
                                        )),
                                        new EndBlock(wcm.getBlockWildcard("whileblockident"))
                                )
                        )
                )
        );

        IterMatchResultCollector matchResultCollector = new IterMatchResultCollector();
        while (mi.hasNext()) {
            mi.advance();
            matchResultCollector.clear();
            if (m.match(mi, matchResultCollector)) {
                // We also have to make sure that the 'synthetic' variables are not used AFTER the loop,
                // without having been assigned first.
                switch (matchResultCollector.getMatchType()) {
                    case WHILE_LOOP:
                        validateAndRewriteWhile(mi, matchResultCollector);
                        break;
                    case DO_LOOP:
                        validateAndRewriteDo(mi, matchResultCollector);
                        break;
                }
                mi.rewind1();
            }

        }

    }

    private enum MatchType {
        NONE,
        WHILE_LOOP,
        DO_LOOP
    }

    private static class IterMatchResultCollector extends AbstractMatchResultIterator {
        MatchType matchType;
        StructuredAssignment iSetup;
        StructuredStatement exit;
        StructuredStatement exitinner;
        StructuredStatement incrStatement;

        StructuredDo doStatement;
        StructuredWhile whileStatement;

        LValue iter_lv;
        Expression collection;

        @Override
        public void clear() {
            matchType = MatchType.NONE;
            iSetup = null;
            exit = null;
            doStatement = null;
            whileStatement = null;
            incrStatement = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            if (name.equals("i$")) {
                iSetup = (StructuredAssignment) statement;
            } else if (name.equals("do")) {
                matchType = MatchType.DO_LOOP;
                doStatement = (StructuredDo) statement;
            } else if (name.equals("while")) {
                matchType = MatchType.WHILE_LOOP;
                whileStatement = (StructuredWhile) statement;
            } else if (name.equals("exit")) {
                exit = statement;
            } else if (name.equals("exitinner")) {
                exitinner = statement;
            } else if (name.equals("incr")) {
                incrStatement = statement;
            } else {
                throw new UnsupportedOperationException("Unexpected match " + name);
            }

        }

        @Override
        public void collectStatementRange(String name, MatchIterator<StructuredStatement> start, MatchIterator<StructuredStatement> end) {
            if (name.equals("body")) {
            } else {
                throw new UnsupportedOperationException("Unexpected match " + name);
            }
        }

        @Override
        public void collectMatches(String name, WildcardMatch wcm) {
            this.iter_lv = wcm.getLValueWildCard("iter").getMatch();
            this.collection = wcm.getExpressionWildCard("collection").getMatch();
        }


        private MatchType getMatchType() {
            return matchType;
        }
    }

    private boolean validateAndRewriteDo(MatchIterator<StructuredStatement> mi, IterMatchResultCollector matchResultCollector) {
        StructuredDo doStatement = matchResultCollector.doStatement;
        Op04StructuredStatement doContainer = doStatement.getContainer();
        Op04StructuredStatement doBody = doStatement.getBody();
        /*
         * We'll need to chop off the first and last statements in this do body
         * (the assignment and the increment).
         */
        if (!(doBody.getStatement() instanceof Block)) {
            return false;
        }

        /*
         * If this is a returning iterator, then that's only valid if the next statement
         * past the end of the loop is a return, or the end of the function.
         */
        StructuredStatement exitInner = matchResultCollector.exitinner;
        boolean copyexit = false;
        if (exitInner instanceof StructuredReturn) {
            boolean legitReturn = false;
            if (mi.isFinished()) {
                legitReturn = true;
            } else {
                StructuredStatement afterLoop = mi.getCurrent();
                int remaining = mi.getRemaining();
                if (remaining == 1 && afterLoop instanceof EndBlock) {
                    copyexit = true;
                    legitReturn = true;
                } else {
                    if (afterLoop.equals(exitInner)) legitReturn = true;
                }
            }
            if (!legitReturn) return false;
        }


        matchResultCollector.incrStatement.getContainer().nopOut();

        LValue iter_lv = matchResultCollector.iter_lv;
        Expression collection = matchResultCollector.collection;

        matchResultCollector.iSetup.getContainer().nopOut();
        matchResultCollector.exit.getContainer().nopOut();

        BlockIdentifier blockidentifier = doStatement.getBlock();
        StructuredStatement replacement = new StructuredIter(blockidentifier, iter_lv, collection, doBody);

        if (copyexit) {
            replacement = Block.getBlockFor(false, replacement, exitInner);
        }

        doContainer.replaceContainedStatement(replacement);

        return true;
    }

    private boolean validateAndRewriteWhile(MatchIterator<StructuredStatement> mi, IterMatchResultCollector matchResultCollector) {
        StructuredWhile whileStatement = matchResultCollector.whileStatement;
        Op04StructuredStatement whileContainer = whileStatement.getContainer();
        Op04StructuredStatement whileBody = whileStatement.getBody();

        matchResultCollector.incrStatement.getContainer().nopOut();

        LValue iter_lv = matchResultCollector.iter_lv;
        Expression collection = matchResultCollector.collection;

        matchResultCollector.iSetup.getContainer().nopOut();

        BlockIdentifier blockidentifier = whileStatement.getBlock();
        StructuredStatement replacement = new StructuredIter(blockidentifier, iter_lv, collection, whileBody);

        whileContainer.replaceContainedStatement(replacement);

        return true;
    }

}
