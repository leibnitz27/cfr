package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.iterrewriter;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.Op04Rewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
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

public class ArrayIterRewriter implements Op04Rewriter {
    private final Options options;
    private final ClassFileVersion classFileVersion;

    public ArrayIterRewriter(Options options, ClassFileVersion classFileVersion) {
        this.options = options;
        this.classFileVersion = classFileVersion;
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        if (!options.getOption(OptionsImpl.ARRAY_ITERATOR, classFileVersion)) return;

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

        LValue iter_lv = wcm.getLValueWildCard("i");
        Expression iter = new LValueExpression(iter_lv);
        // This could be an actual array, or it could be a function, or even a reassignment!
        Expression orig_array = wcm.getExpressionWildCard("array");
        LValue i$_lv = wcm.getLValueWildCard("i$");
        Expression i$ = new LValueExpression(i$_lv);
        LValue len$_lv = wcm.getLValueWildCard("len$");
        Expression len$ = new LValueExpression(len$_lv);
        Expression array$ = new LValueExpression(array$_lv);

        Matcher<StructuredStatement> m = new ResetAfterTest(wcm,
                new MatchSequence(
                        new CollectMatch("array$", new StructuredAssignment(array$_lv, orig_array)),
                        new CollectMatch("len$", new StructuredAssignment(len$_lv, new ArrayLength(array$))),
                        new MatchOneOf(
                                new MatchSequence(
                                        new CollectMatch("i$", new StructuredAssignment(i$_lv, new Literal(TypedLiteral.getInt(0)))),
                                        new CollectMatch("do", new StructuredDo(null, null, wcm.getBlockIdentifier("doblockident"))),
                                        new BeginBlock(wcm.getBlockWildcard("doblock")),
                                        new CollectMatch("exit", new StructuredIf(new ComparisonOperation(i$, len$, CompOp.GTE), null)),
                                        new BeginBlock(null),
                                        new MatchOneOf(
                                                new StructuredBreak(wcm.getBlockIdentifier("doblockident"), false),
                                                new StructuredReturn(wcm.getExpressionWildCard("returnvalue"), null),
                                                new StructuredReturn(null, null)
                                        ),
                                        new EndBlock(null),
                                        new CollectMatch("assigniter", new StructuredAssignment(iter_lv, new ArrayIndex(array$, i$))),
                                        new CollectMatchRange("body", new KleenePlus(new Negated(
                                                new MatchOneOf(
                                                        // We abort on both the end of block and the ++$i, to save us walking the entire
                                                        // code, if there's no pre-mutation.
                                                        new StructuredExpressionStatement(new ArithmeticPreMutationOperation(i$_lv, ArithOp.PLUS), true),
                                                        new EndBlock(wcm.getBlockWildcard("doblock")))
                                        )
                                        )),
                                        new CollectMatch("incr", new StructuredExpressionStatement(new ArithmeticPreMutationOperation(i$_lv, ArithOp.PLUS), true)),
                                        new EndBlock(wcm.getBlockWildcard("doblock"))
                                ), // end do version
                                // for version.  Quite a bit simpler!
                                new MatchSequence(
                                        new CollectMatch("for", new StructuredFor(
                                                new ComparisonOperation(i$, len$, CompOp.LT),
                                                new AssignmentSimple(i$_lv, new Literal(TypedLiteral.getInt(0))),
                                                ListFactory.<AbstractAssignmentExpression>newImmutableList(new ArithmeticPreMutationOperation(i$_lv, ArithOp.PLUS)),
                                                null, wcm.getBlockIdentifier("doblockident")
                                        )),
                                        new BeginBlock(wcm.getBlockWildcard("forblock")),
                                        new CollectMatch("assigniter", new StructuredAssignment(iter_lv, new ArrayIndex(array$, i$))),
                                        new CollectMatchRange("body", new KleenePlus(new Negated(
                                                new EndBlock(wcm.getBlockWildcard("doblock")))
                                        )),
                                        new EndBlock(wcm.getBlockWildcard("forblock"))
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
                    case FOR_LOOP:
                        validateAndRewriteFor(matchResultCollector);
                        break;
                    case DO_LOOP:
                        validateAndRewriteDo(matchResultCollector);
                        break;
                }
                mi.rewind1();
            }

        }

    }

    private enum MatchType {
        NONE,
        FOR_LOOP,
        DO_LOOP
    }

    private static class IterMatchResultCollector extends AbstractMatchResultIterator {
        MatchType matchType;
        StructuredAssignment arraySetup;
        StructuredAssignment lenSetup;
        StructuredAssignment iSetup;
        StructuredStatement exit;
        StructuredStatement incrStatement;
        StructuredStatement assignIter;

        StructuredDo doStatement;
        StructuredFor forStatement;

        LValue iter_lv;
        Expression orig_array;

        @Override
        public void clear() {
            matchType = MatchType.NONE;
            arraySetup = lenSetup = iSetup = null;
            exit = null;
            doStatement = null;
            forStatement = null;
            incrStatement = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            if (name.equals("array$")) {
                arraySetup = (StructuredAssignment) statement;
            } else if (name.equals("len$")) {
                lenSetup = (StructuredAssignment) statement;
            } else if (name.equals("i$")) {
                iSetup = (StructuredAssignment) statement;
            } else if (name.equals("do")) {
                matchType = MatchType.DO_LOOP;
                doStatement = (StructuredDo) statement;
            } else if (name.equals("for")) {
                matchType = MatchType.FOR_LOOP;
                forStatement = (StructuredFor) statement;
            } else if (name.equals("exit")) {
                exit = statement;
            } else if (name.equals("incr")) {
                incrStatement = statement;
            } else if (name.equals("assigniter")) {
                assignIter = statement;
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
            this.iter_lv = wcm.getLValueWildCard("i").getMatch();
            this.orig_array = wcm.getExpressionWildCard("array").getMatch();
        }


        private MatchType getMatchType() {
            return matchType;
        }
    }

    private boolean validateAndRewriteFor(IterMatchResultCollector matchResultCollector) {
        StructuredFor structuredFor = matchResultCollector.forStatement;
        Op04StructuredStatement forContainer = structuredFor.getContainer();
        Op04StructuredStatement forBody = structuredFor.getBody();

        if (!(forBody.getStatement() instanceof Block)) {
            return false;
        }

        BlockIdentifier blockidentifier = structuredFor.getBlock();

        LValue iter_lv = matchResultCollector.iter_lv;
        Expression array = matchResultCollector.orig_array;

        matchResultCollector.arraySetup.getContainer().nopOut();
        matchResultCollector.lenSetup.getContainer().nopOut();
        matchResultCollector.assignIter.getContainer().nopOut();

        StructuredStatement forIter = new StructuredIter(blockidentifier, iter_lv, array, forBody);
        forContainer.replaceContainedStatement(forIter);


        return true;
    }

    private boolean validateAndRewriteDo(IterMatchResultCollector matchResultCollector) {
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

        matchResultCollector.incrStatement.getContainer().nopOut();

        LValue iter_lv = matchResultCollector.iter_lv;
        Expression array = matchResultCollector.orig_array;

        matchResultCollector.iSetup.getContainer().nopOut();
        matchResultCollector.arraySetup.getContainer().nopOut();
        matchResultCollector.lenSetup.getContainer().nopOut();
        matchResultCollector.exit.getContainer().nopOut();
        matchResultCollector.assignIter.getContainer().nopOut();

        BlockIdentifier blockidentifier = doStatement.getBlock();
        StructuredStatement forIter = new StructuredIter(blockidentifier, iter_lv, array, doBody);
        doContainer.replaceContainedStatement(forIter);

        return true;
    }
}
