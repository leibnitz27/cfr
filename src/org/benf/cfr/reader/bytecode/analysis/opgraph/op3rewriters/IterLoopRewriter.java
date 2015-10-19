package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollectorSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.Predicate;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class IterLoopRewriter {

    private static Pair<ConditionalExpression, ConditionalExpression> getSplitAnd(ConditionalExpression cnd) {
        if (!(cnd instanceof BooleanOperation)) return Pair.make(cnd, null);
        BooleanOperation op = (BooleanOperation)cnd;
        if (op.getOp() != BoolOp.AND) return Pair.make(cnd, null);
        return Pair.make(op.getLhs(), op.getRhs());
    }

    /* Given a for loop
 *
 * array
 * bound = array.length
 * for ( index ; index < bound ; index++) {
 *   a = array[index]
 * }
 *
 * rewrite as
 *
 * for ( a : array ) {
 * }
 *
 * /however/ - it is only safe to do this if NEITHER index / bound / array are assigned to inside the loop.
 *
 * TODO : The tests in here are very rigid (and gross!), and need loosening up when it's working.
 */
    private static boolean rewriteArrayForLoop(final Op03SimpleStatement loop, List<Op03SimpleStatement> statements) {

        /*
         * loop should have one back-parent.
         */
        Op03SimpleStatement preceeding = Misc.findSingleBackSource(loop);
        if (preceeding == null) return false;

        ForStatement forStatement = (ForStatement) loop.getStatement();

        WildcardMatch wildcardMatch = new WildcardMatch();

        if (!wildcardMatch.match(
                new AssignmentSimple(wildcardMatch.getLValueWildCard("iter"), new Literal(TypedLiteral.getInt(0))),
                forStatement.getInitial())) return false;

        LValue originalLoopVariable = wildcardMatch.getLValueWildCard("iter").getMatch();

        // Assignments are fiddly, as they can be assignmentPreChange or regular Assignment.
        List<AbstractAssignmentExpression> assignments = forStatement.getAssignments();
        if (assignments.size() != 1) return false;
        AbstractAssignmentExpression assignment = assignments.get(0);
        boolean incrMatch = assignment.isSelfMutatingOp1(originalLoopVariable, ArithOp.PLUS);
        if (!incrMatch) return false;

        /*
         * Potential problem is if the condition has been rolled in with another - we need to find the LHS of a deep
         * and tree.  If there's remainder, we'll need to pull it out into a break, if we do this refactor.
         */
        ConditionalExpression condition = forStatement.getCondition();
        Pair<ConditionalExpression, ConditionalExpression> condpr = getSplitAnd(condition);

        if (!wildcardMatch.match(
                new ComparisonOperation(
                        new LValueExpression(originalLoopVariable),
                        new LValueExpression(wildcardMatch.getLValueWildCard("bound")),
                        CompOp.LT), condpr.getFirst())) {
            return false;
        }

        LValue originalLoopBound = wildcardMatch.getLValueWildCard("bound").getMatch();

        // Bound should have been constructed RECENTLY, and should be an array length.
        // TODO: Let's just check the single backref from the for loop test.
        if (!wildcardMatch.match(
                new AssignmentSimple(originalLoopBound, new ArrayLength(new LValueExpression(wildcardMatch.getLValueWildCard("array")))),
                preceeding.getStatement())) return false;

        LValue originalArray = wildcardMatch.getLValueWildCard("array").getMatch();

        Expression arrayStatement = new LValueExpression(originalArray);
        Op03SimpleStatement prepreceeding = null;
        /*
         * if we're following the JDK pattern, we'll have something assigned to array.
         */
        if (preceeding.getSources().size() == 1) {
            if (wildcardMatch.match(
                    new AssignmentSimple(originalArray, wildcardMatch.getExpressionWildCard("value")),
                    preceeding.getSources().get(0).getStatement())) {
                prepreceeding = preceeding.getSources().get(0);
                arrayStatement = wildcardMatch.getExpressionWildCard("value").getMatch();
            }
        }

        /* If we've had to pull out a RHS from the condition, that's what we're interested in, not a
         * loop start.
         */
        Op03SimpleStatement realLoopStart = loop.getTargets().get(0);
        Op03SimpleStatement loopStart = realLoopStart;
        if (condpr.getSecond() != null) {
            /*
             * If we're using the LHS of a conjunction, we need to assume the RHS
             * of the conjunction is doing the work of the loop start.
             * (we'll need to move it into the loop if we actually use this loop!)
             */
            IfStatement fakeLoopStm = new IfStatement(condpr.getSecond().getNegated());
            fakeLoopStm.setJumpType(JumpType.BREAK);
            loopStart = new Op03SimpleStatement(loopStart.getBlockIdentifiers(), fakeLoopStm, loopStart.getIndex().justBefore());
        }

        // for the 'non-taken' branch of the test, we expect to find an assignment to a value.
        // TODO : This can be pushed into the loop, as long as it's not after a conditional.
        WildcardMatch.LValueWildcard sugariterWC = wildcardMatch.getLValueWildCard("sugariter");
        Expression arrIndex = new ArrayIndex(new LValueExpression(originalArray), new LValueExpression(originalLoopVariable));
        boolean hiddenIter = false;
        if (!wildcardMatch.match(
                new AssignmentSimple(sugariterWC, arrIndex),
                loopStart.getStatement())) {
            // If the assignment's been pushed down into a conditional, we could have
            // if ((i = a[x]) > 3).  This is why we've avoided pushing that down. :(
            Set<Expression> poison = SetFactory.<Expression>newSet(new LValueExpression(originalLoopVariable));
            if (!Misc.findHiddenIter(loopStart.getStatement(), sugariterWC, arrIndex, poison)) {
                return false;
            }
            hiddenIter = true;
        }

        LValue sugarIter = sugariterWC.getMatch();

        // It's probably valid.  We just have to make sure that array and index aren't assigned to anywhere in the loop
        // body.
        final BlockIdentifier forBlock = forStatement.getBlockIdentifier();
        List<Op03SimpleStatement> statementsInBlock = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getBlockIdentifiers().contains(forBlock);
            }
        });

        /*
         * It's not simple enough to check if they're assigned to - we also have to verify that i$ (for example ;) isn't
         * even USED anywhere else.
         */
        LValueUsageCollectorSimple usageCollector = new LValueUsageCollectorSimple();
        final Set<LValue> cantUpdate = SetFactory.newSet(originalArray, originalLoopBound, originalLoopVariable);

        for (Op03SimpleStatement inBlock : statementsInBlock) {
            if (inBlock == loopStart) continue;
            Statement inStatement = inBlock.getStatement();
            inStatement.collectLValueUsage(usageCollector);
            for (LValue cantUse : cantUpdate) {
                if (usageCollector.isUsed(cantUse)) {
                    return false;
                }
            }
            LValue updated = inStatement.getCreatedLValue();
            if (updated == null) continue;
            if (cantUpdate.contains(updated)) {
                return false;
            }
        }

        /*
         * We shouldn't have to do this, because we should be doing this at a point where we've discovered
         * scope better (op04?), but now, verify that no reachable statements (do a dfs from the end point of
         * the loop with no retry) use either the iterator or the temp value without assigning them first.
         * (or are marked as being part of the block, as we've already verified them)
         * (or are the initial assignment statements).
         */
        final AtomicBoolean res = new AtomicBoolean();
        GraphVisitor<Op03SimpleStatement> graphVisitor = new GraphVisitorDFS<Op03SimpleStatement>(loop,
                new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                    @Override
                    public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                        if (!(loop == arg1 || arg1.getBlockIdentifiers().contains(forBlock))) {
                            // need to check it.
                            Statement inStatement = arg1.getStatement();

                            if (inStatement instanceof AssignmentSimple) {
                                AssignmentSimple assignmentSimple = (AssignmentSimple) inStatement;
                                if (cantUpdate.contains(assignmentSimple.getCreatedLValue())) return;
                            }
                            LValueUsageCollectorSimple usageCollector = new LValueUsageCollectorSimple();
                            inStatement.collectLValueUsage(usageCollector);
                            for (LValue cantUse : cantUpdate) {
                                if (usageCollector.isUsed(cantUse)) {
                                    res.set(true);
                                    return;
                                }
                            }
                        }
                        for (Op03SimpleStatement target : arg1.getTargets()) {
                            arg2.enqueue(target);
                        }
                    }
                });
        graphVisitor.process();
        if (res.get()) {
            return false;
        }


        loop.replaceStatement(new ForIterStatement(forBlock, sugarIter, arrayStatement));
        if (loopStart != realLoopStart) {
            if (hiddenIter) {
                /*
                 * TODO : this probably occurs dozens of times.  Factor out.
                 */
                // Insert between loopstart and loop.
                loop.replaceTarget(realLoopStart, loopStart);
                realLoopStart.replaceSource(loop, loopStart);
                loopStart.addSource(loop);
                loopStart.addTarget(realLoopStart);
                Op03SimpleStatement endStm = loop.getTargets().get(1);
                loopStart.addTarget(endStm);
                endStm.addSource(loopStart);
                Misc.replaceHiddenIter(loopStart.getStatement(), sugariterWC.getMatch(), arrIndex);
                // This is an infrequent op, and we want to preserve sort
                statements.add(statements.indexOf(realLoopStart), loopStart);
            } else {
                // Nothing to do - pretend we nopped it out!
            }
        } else {
            if (hiddenIter) {
                /*
                 * If there was a fake loop start, we need to move it in before this.
                 */
                Misc.replaceHiddenIter(loopStart.getStatement(), sugariterWC.getMatch(), arrIndex);
            } else {
                loopStart.nopOut();
            }
        }
        preceeding.nopOut();
        if (prepreceeding != null) {
            prepreceeding.nopOut();
        }

        return true;
    }


    public static void rewriteArrayForLoops(List<Op03SimpleStatement> statements) {
        for (Op03SimpleStatement loop : Functional.filter(statements, new TypeFilter<ForStatement>(ForStatement.class))) {
            rewriteArrayForLoop(loop, statements);
        }
    }


    /*
     * We're being called /after/ optimiseForTypes, so we expect an expression set of the form
     *
     * [x] = [y].iterator()
     * while ([x].hasNext()) {
     *   [a] = [x].next();
     * }
     */
    private static void rewriteIteratorWhileLoop(final Op03SimpleStatement loop, List<Op03SimpleStatement> statements) {
        WhileStatement whileStatement = (WhileStatement) loop.getStatement();

        /*
         * loop should have one back-parent.
         */
        Op03SimpleStatement preceeding = Misc.findSingleBackSource(loop);
        if (preceeding == null) return;

        WildcardMatch wildcardMatch = new WildcardMatch();

        ConditionalExpression condition = whileStatement.getCondition();
        Pair<ConditionalExpression, ConditionalExpression> condpr = getSplitAnd(condition);

        if (!wildcardMatch.match(
                new BooleanExpression(
                        wildcardMatch.getMemberFunction("hasnextfn", "hasNext", new LValueExpression(wildcardMatch.getLValueWildCard("iterable")))
                ),
                condpr.getFirst())) return;

        final LValue iterable = wildcardMatch.getLValueWildCard("iterable").getMatch();

        Op03SimpleStatement realLoopStart = loop.getTargets().get(0);
        Op03SimpleStatement loopStart = realLoopStart;
        if (condpr.getSecond() != null) {
            /*
             * If we're using the LHS of a conjunction, we need to assume the RHS
             * of the conjunction is doing the work of the loop start.
             * (we'll need to move it into the loop if we actually use this loop!)
             */
            IfStatement fakeLoopStm = new IfStatement(condpr.getSecond().getNegated());
            fakeLoopStm.setJumpType(JumpType.BREAK);
            loopStart = new Op03SimpleStatement(loopStart.getBlockIdentifiers(), fakeLoopStm, loopStart.getIndex().justBefore());
        }
        // for the 'non-taken' branch of the test, we expect to find an assignment to a value.
        // TODO : This can be pushed into the loop, as long as it's not after a conditional.
        boolean isCastExpression = false;
        boolean hiddenIter = false;
        WildcardMatch.LValueWildcard sugariterWC = wildcardMatch.getLValueWildCard("sugariter");
        Expression nextCall = wildcardMatch.getMemberFunction("nextfn", "next", new LValueExpression(wildcardMatch.getLValueWildCard("iterable")));
        if (wildcardMatch.match(
                new AssignmentSimple(sugariterWC, nextCall),
                loopStart.getStatement())) {
        } else if (wildcardMatch.match(
                new AssignmentSimple(sugariterWC,
                        wildcardMatch.getCastExpressionWildcard("cast", nextCall)),
                loopStart.getStatement())) {
            // It's a cast expression - so we know that there's a type we might be able to push back up.
            isCastExpression = true;
        } else {
            // Try seeing if it's a hidden iter, which has been pushed inside a conditional
            Set<Expression> poison = SetFactory.<Expression>newSet(new LValueExpression(iterable));
            if (!Misc.findHiddenIter(loopStart.getStatement(), sugariterWC, nextCall, poison)) {
                return;
            }
            hiddenIter = true;
        }

        LValue sugarIter = wildcardMatch.getLValueWildCard("sugariter").getMatch();

        if (!wildcardMatch.match(
                new AssignmentSimple(wildcardMatch.getLValueWildCard("iterable"),
                        wildcardMatch.getMemberFunction("iterator", "iterator", wildcardMatch.getExpressionWildCard("iteratorsource"))),
                preceeding.getStatement())) return;

        Expression iterSource = wildcardMatch.getExpressionWildCard("iteratorsource").getMatch();

        // It's probably valid.  We just have to make sure that array and index aren't assigned to anywhere in the loop
        // body.
        final BlockIdentifier blockIdentifier = whileStatement.getBlockIdentifier();
        List<Op03SimpleStatement> statementsInBlock = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getBlockIdentifiers().contains(blockIdentifier);
            }
        });

        /*
         * It's not simple enough to check if they're assigned to - we also have to verify that i$ (for example ;) isn't
         * even USED anywhere else.
         */
        LValueUsageCollectorSimple usageCollector = new LValueUsageCollectorSimple();
        for (Op03SimpleStatement inBlock : statementsInBlock) {
            if (inBlock == loopStart) continue;
            Statement inStatement = inBlock.getStatement();
            inStatement.collectLValueUsage(usageCollector);
            if (usageCollector.isUsed(iterable)) {
                return;
            }
            LValue updated = inStatement.getCreatedLValue();
            if (updated == null) continue;
            if (updated.equals(sugarIter) || updated.equals(iterable)) {
                return;
            }
        }

        /*
         * Iterator should either be supplying raw Objects, or be generically typed.
         * We should check the base type to make sure we're assigning to something that is a base
         * class of the iterator generic.
         *
         * Unfortunately, at the moment, this breaks iterated type hints.
         */
        JavaTypeInstance iteratorSourceType = iterSource.getInferredJavaType().getJavaTypeInstance();
        BindingSuperContainer supers = iteratorSourceType.getBindingSupers();
        if (!supers.containsBase(TypeConstants.ITERABLE)) return;

        /*
         * We shouldn't have to do this, because we should be doing this at a point where we've discovered
         * scope better (op04?), but now, verify that no reachable statements (do a dfs from the end point of
         * the loop with no retry) use either the iterator or the temp value without assigning them first.
         * (or are marked as being part of the block, as we've already verified them)
         * (or are the initial assignment statements).
         */
        final AtomicBoolean res = new AtomicBoolean();
        GraphVisitor<Op03SimpleStatement> graphVisitor = new GraphVisitorDFS<Op03SimpleStatement>(loop,
                new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                    @Override
                    public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                        if (!(loop == arg1 || arg1.getBlockIdentifiers().contains(blockIdentifier))) {
                            // need to check it.
                            Statement inStatement = arg1.getStatement();

                            if (inStatement instanceof AssignmentSimple) {
                                AssignmentSimple assignmentSimple = (AssignmentSimple) inStatement;
                                if (iterable.equals(assignmentSimple.getCreatedLValue())) return;
                            }
                            LValueUsageCollectorSimple usageCollector = new LValueUsageCollectorSimple();
                            inStatement.collectLValueUsage(usageCollector);
                            if (usageCollector.isUsed(iterable)) {
                                res.set(true);
                                return;
                            }
                        }
                        for (Op03SimpleStatement target : arg1.getTargets()) {
                            arg2.enqueue(target);
                        }
                    }
                });
        graphVisitor.process();
        if (res.get()) {
            return;
        }

        loop.replaceStatement(new ForIterStatement(blockIdentifier, sugarIter, iterSource));
        if (loopStart != realLoopStart) {
            if (hiddenIter) {
                /*
                 * TODO : this probably occurs dozens of times.  Factor out.
                 */
                // Insert between loopstart and loop.
                loop.replaceTarget(realLoopStart, loopStart);
                realLoopStart.replaceSource(loop, loopStart);
                loopStart.addSource(loop);
                loopStart.addTarget(realLoopStart);
                Op03SimpleStatement endStm = loop.getTargets().get(1);
                loopStart.addTarget(endStm);
                endStm.addSource(loopStart);
                Misc.replaceHiddenIter(loopStart.getStatement(), sugariterWC.getMatch(), nextCall);
                // This is an infrequent op, and we want to preserve sort
                statements.add(statements.indexOf(realLoopStart), loopStart);
            } else {
                // Nothing to do - pretend we nopped it out!
            }
        } else {
            if (hiddenIter) {
                Misc.replaceHiddenIter(loopStart.getStatement(), sugariterWC.getMatch(), nextCall);
            } else {
                loopStart.nopOut();
            }
        }
        preceeding.nopOut();
    }

    public static void rewriteIteratorWhileLoops(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> loops = Functional.filter(statements, new TypeFilter<WhileStatement>(WhileStatement.class));
        for (Op03SimpleStatement loop : loops) {
            rewriteIteratorWhileLoop(loop, statements);
        }
    }

}
