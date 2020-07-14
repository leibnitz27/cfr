package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.DoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.WhileStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Similar to the jumps into do rewriter, however we allow (grudgingly) a small amount of code to be copied.
 *
 * As with jumps-into-do, this is a 'regrettable' transform, as it changes the original code.
 *
 * It's cleaner, as it doesn't introduce a spurious test, however it COULD lead to massive duplication.
 * We therefore limit the size of code it's permitted to clone.
 */
public class JumpsIntoLoopCloneRewriter {

    private int maxDepth;

    JumpsIntoLoopCloneRewriter(Options options) {
        this.maxDepth = options.getOption(OptionsImpl.AGGRESSIVE_DO_COPY);
    }

    /*
     * Walk all while loops - if there's a jump from outside the loop to 'just' (where just (A) is defined in configuration,
     * but must be well structured and is ideally 1 at most) before the return jump, then we can replace this jump
     * with a copy of A, and a jump to the start of the loop.
     *
     * The same can be said for do loops, however we must also test the condition.
     *
     */
    public void rewrite(List<Op03SimpleStatement> op03SimpleParseNodes, DecompilerComments comments) {

        List<Op03SimpleStatement> addThese = ListFactory.newList();
        for (Op03SimpleStatement stm : op03SimpleParseNodes) {
            Statement statement = stm.getStatement();
            if (statement instanceof DoStatement) {
                refactorDo(addThese, stm, ((DoStatement) statement).getBlockIdentifier());
            } else if (statement instanceof WhileStatement) {
                refactorWhile(addThese, stm, ((WhileStatement) statement).getBlockIdentifier());
            }
        }

        if (!addThese.isEmpty()) {
            // This is going to generate pretty grotty code, (see LoopFakery tests), so we want to
            // apologise....
            op03SimpleParseNodes.addAll(addThese);
            comments.addComment(DecompilerComment.IMPOSSIBLE_LOOP_WITH_COPY);
            Cleaner.sortAndRenumberInPlace(op03SimpleParseNodes);
        }
    }

    /*
     * This is more complicated than the while case, as we have to lift and handle the predicate as well.
     *
     * goto a:
     *
     * BLAH
     *
     * x: do {
     *   A
     *   B
     *   C
     * a:D
     * } while (foo)
     * b:
     * -->
     *
     * D
     * if (!foo) goto b
     *
     * BLAH
     *
     * x : do {
     *   A
     *   B
     *   C
     * a:D
     * } while (foo)
     * b:
     */
    private void refactorDo(List<Op03SimpleStatement> addThese, Op03SimpleStatement stm, BlockIdentifier ident) {
        // NB: Posslast will jump to the statement immediately FOLLOWING the do.
        Op03SimpleStatement firstContained = stm.getTargets().get(0);
        Op03SimpleStatement possLast = getPossLast(firstContained, ident);
        if (possLast == null) return;
        if (!(possLast.getStatement() instanceof WhileStatement)) return;
        WhileStatement whileStatement = (WhileStatement) possLast.getStatement();
        if (whileStatement.getBlockIdentifier() != ident) return;
        Op03SimpleStatement afterWhile = possLast.getTargets().get(0);
        final Map<Op03SimpleStatement, Op03SimpleStatement> candidates = MapFactory.newOrderedMap();
        GraphVisitor<Op03SimpleStatement> gv = visitCandidates(ident, possLast, candidates);
        Set<Op03SimpleStatement> visited = SetFactory.newSet(gv.getVisitedNodes());
        for (Map.Entry<Op03SimpleStatement, Op03SimpleStatement> candidate : candidates.entrySet()) {
            Op03SimpleStatement caller = candidate.getKey();
            if (caller == stm) continue;
            Op03SimpleStatement target = candidate.getValue();
            /*
             * If this is a while loop, fine, we can finish with a jump to the while.  If it's a do
             * loop, we need to finish with a test of the condition, and a jump to the start,
             * or a jump after. (yeuch).
             */
            Map<Op03SimpleStatement, Op03SimpleStatement> copies = new IdentityHashMap<Op03SimpleStatement, Op03SimpleStatement>();
            copies.put(caller, caller);
            /*
             * Ok, we need to clone (if legal), the set of entries between target and posslast.
             * if we ever leave 'visited', this is not a legal rewrite.
             * (strictly speaking, we could handle it, if we leave visited for something OUTSIDE
             * the loop - but I need testcases to prove that first!)
             */
            InstrIndex idx = caller.getIndex();
            // if (loopcondition) -> do
            // else afterwhile
            ConditionalExpression condition = whileStatement.getCondition();
            if (condition == null) {
                condition = new BooleanExpression(Literal.TRUE);
            }
            IfStatement newConditionStatement = new IfStatement(BytecodeLoc.TODO, condition);
            // we'll move these indices later.
            Op03SimpleStatement newCondition = new Op03SimpleStatement(caller.getBlockIdentifiers(), newConditionStatement, possLast.getSSAIdentifiers(), idx);
            Op03SimpleStatement jumpToAfterWhile = new Op03SimpleStatement(caller.getBlockIdentifiers(), new GotoStatement(BytecodeLoc.TODO), caller.getSSAIdentifiers(), idx);
            copies.put(afterWhile, jumpToAfterWhile);
            copies.put(possLast, newCondition);
            Set<Op03SimpleStatement> addSources = SetFactory.newSet(newCondition, jumpToAfterWhile);
            List<Op03SimpleStatement> copy = copyBlock(stm, caller, target, possLast, visited, ident, addSources, copies);
            if (copy == null || copy.isEmpty()) {
                return;
            }
            idx = copy.get(copy.size()-1).getIndex();
            idx = idx.justAfter();
            newCondition.setIndex(idx);
            idx = idx.justAfter();
            jumpToAfterWhile.setIndex(idx);

            for (Op03SimpleStatement copied : copies.values()) {
                if (copied.getTargets().contains(newCondition)) {
                    newCondition.addSource(copied);
                }
            }

            boolean usedToFall = handleConditionalCaller(stm, caller, target, copies);

            /*
             * and now some tidying ...
             */
            if (afterWhile == firstContained) afterWhile = stm;
            if (usedToFall && jumpToAfterWhile.getSources().isEmpty()) {
                newConditionStatement.negateCondition();
                newCondition.addTarget(stm);
                stm.addSource(newCondition);
                newCondition.addTarget(afterWhile);
                afterWhile.addSource(newCondition);
                copy.add(newCondition);
            } else {
                newCondition.addTarget(jumpToAfterWhile);
                newCondition.addTarget(stm);
                stm.addSource(newCondition);
                jumpToAfterWhile.addSource(newCondition);
                jumpToAfterWhile.addTarget(afterWhile);
                afterWhile.addSource(jumpToAfterWhile);
                copy.add(newCondition);
                copy.add(jumpToAfterWhile);
            }
            nopPointlessCondition(newConditionStatement, newCondition);
            addThese.addAll(copy);
        }
    }

    /*
     * goto a:
     *
     * BLAH
     *
     * x: while (foo) {
     *   A
     *   B
     *   C
     * a:D
     * }
     *
     * -->
     *
     * D
     * goto x
     *
     * BLAH
     *
     * x : while (foo) {
     *   A
     *   B
     *   C
     * a:D
     * }
     *
     */
    private void refactorWhile(List<Op03SimpleStatement> addThese, Op03SimpleStatement stm, BlockIdentifier ident) {
        Op03SimpleStatement possLast = getPossLast(stm, ident);
        if (possLast == null) return;
        final Map<Op03SimpleStatement, Op03SimpleStatement> candidates = MapFactory.newOrderedMap();
        GraphVisitor<Op03SimpleStatement> gv = visitCandidates(ident, possLast, candidates);
        Set<Op03SimpleStatement> visited = SetFactory.newSet(gv.getVisitedNodes());
        for (Map.Entry<Op03SimpleStatement, Op03SimpleStatement> candidate : candidates.entrySet()) {
            Op03SimpleStatement caller = candidate.getKey();
            if (caller == stm) continue;
            Op03SimpleStatement target = candidate.getValue();
            /*
             * If this is a while loop  , fine, we can finish with a jump to the while.  If it's a do
             * loop, we need to finish with a test of the condition, and a jump to the start,
             * or a jump after. (yeuch).
             */
            Map<Op03SimpleStatement, Op03SimpleStatement> copies = new IdentityHashMap<Op03SimpleStatement, Op03SimpleStatement>();
            copies.put(caller, caller);
            copies.put(stm, stm);
            /*
             * Ok, we need to clone (if legal), the set of entries between target and posslast.
             * if we ever leave 'visited', this is not a legal rewrite.
             * (strictly speaking, we could handle it, if we leave visited for something OUTSIDE
             * the loop - but I need testcases to prove that first!)
             */
            List<Op03SimpleStatement> copy = copyBlock(stm, caller, target, possLast, visited, ident, SetFactory.<Op03SimpleStatement>newSet(), copies);
            if (copy == null || copy.isEmpty()) {
                return;
            }

            @SuppressWarnings("unused")
            boolean usedToFall = handleConditionalCaller(stm, caller, target, copies);

            Op03SimpleStatement lastCopy = copies.get(possLast);
            stm.addSource(lastCopy);
            addThese.addAll(copy);
        }
    }

    private boolean handleConditionalCaller(Op03SimpleStatement stm, Op03SimpleStatement caller, Op03SimpleStatement target, Map<Op03SimpleStatement, Op03SimpleStatement> copies) {
        Op03SimpleStatement targetCopy = copies.get(target);
        /*
         * If the original caller was conditional, we want to invert the condition, and swap the targets.
         */
        target.removeSource(caller);
        boolean usedToFall = false;
        if (caller.getStatement() instanceof IfStatement) {
            if (caller.getTargets().get(0) == stm) {
                usedToFall = true;
            }
            caller.removeGotoTarget(target);
            caller.getTargets().add(0, targetCopy);
            /*
             * If the caller was a condition, we need to invert it.
             */
            ((IfStatement) caller.getStatement()).negateCondition();
        } else {
            caller.replaceTarget(target, targetCopy);
        }
        return usedToFall;
    }

    private void nopPointlessCondition(IfStatement newConditionStatement, Op03SimpleStatement newCondition) {
        List<Op03SimpleStatement> targets = newCondition.getTargets();
        Op03SimpleStatement t0 = targets.get(0);
        Op03SimpleStatement s1 = Misc.followNopGotoChain(t0, false, true);
        Op03SimpleStatement t1 = targets.get(1);
        Op03SimpleStatement s2 = Misc.followNopGotoChain(t1, false, true);
        if (s1 == s2 && null != newConditionStatement.getCondition().getComputedLiteral(MapFactory.<LValue, Literal>newMap())) {
            t0.removeSource(newCondition);
            t1.removeSource(newCondition);
            newCondition.getTargets().clear();
            newCondition.getTargets().add(s1);
            s1.addSource(newCondition);
            newCondition.replaceStatement(new Nop());
        }
    }

    private Op03SimpleStatement getPossLast(Op03SimpleStatement stm, BlockIdentifier ident) {
        List<Op03SimpleStatement> lasts = stm.getSources();
        Collections.sort(lasts, new CompareByIndex(false));
        Op03SimpleStatement possLast = lasts.get(0);
        if (!possLast.getBlockIdentifiers().contains(ident)) {
            return null;
        }
        Op03SimpleStatement linearlyNext = possLast.getLinearlyNext();
        if (linearlyNext != null && linearlyNext.getBlockIdentifiers().contains(ident)) {
            return null;
        }
        return possLast;
    }

    /*
     * linearly scan N back.  If there's a jump in from outside, we could clone the N, as long as it's
     * possible to treat it as an isolated block.
     * (i.e. if we remove all jumps TO the block from the outside, it still makes sense).
     * We can do this by considering up to N back, but only looking at targets.  If the target is in the
     * set under consideration, it's possible to clone.
     *
     * We can establish a target set by doing a depth first search with a max depth of N.
     */
    private GraphVisitor<Op03SimpleStatement> visitCandidates(final BlockIdentifier blockIdent, Op03SimpleStatement possLast, final Map<Op03SimpleStatement, Op03SimpleStatement> candidates) {
        final Map<Op03SimpleStatement, Integer> depthMap = MapFactory.newIdentityMap();
        depthMap.put(possLast, 0);
        GraphVisitor<Op03SimpleStatement> gv = new GraphVisitorDFS<Op03SimpleStatement>(possLast, new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
            @Override
            public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                int depth = depthMap.get(arg1);
                if (depth > maxDepth) return;
                for (Op03SimpleStatement source : arg1.getSources()) {
                    if (source.getBlockIdentifiers().contains(blockIdent)) {
                        depthMap.put(source, depth + 1);
                        arg2.enqueue(source);
                    } else {
                        candidates.put(source, arg1);
                    }
                }
            }
        });
        gv.process();
        return gv;
    }

    private List<Op03SimpleStatement> copyBlock(final Op03SimpleStatement stm, final Op03SimpleStatement caller, final Op03SimpleStatement start, final Op03SimpleStatement end, final Set<Op03SimpleStatement> valid,
                                                final BlockIdentifier containedIn,
                                                Set<Op03SimpleStatement> addSources,
                                                final Map<Op03SimpleStatement, Op03SimpleStatement> orig2copy
    ) {
        final List<Op03SimpleStatement> copyThese = ListFactory.newList();
        final boolean[] failed = { false };
        GraphVisitor<Op03SimpleStatement> gv = new GraphVisitorDFS<Op03SimpleStatement>(start, new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
            @Override
            public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                if (orig2copy.containsKey(arg1)) return;
                if (valid.contains(arg1)) {
                    copyThese.add(arg1);
                    arg2.enqueue(arg1.getTargets());
                    return;
                }
                if (arg1.getBlockIdentifiers().contains(containedIn)) {
                    failed[0] = true;
                }
            }
        });
        gv.process();
        if (failed[0]) {
            return null;
        }
        Collections.sort(copyThese, new CompareByIndex());
        List<Op03SimpleStatement> copies = ListFactory.newList();
        // Note - the expected blocks means that we do NOT currently allow jumping into nested structures.
        Set<BlockIdentifier> expectedBlocks = end.getBlockIdentifiers();

        CloneHelper cloneHelper = new CloneHelper();
        InstrIndex idx = caller.getIndex().justAfter();
        for (Op03SimpleStatement copyThis : copyThese) {
            Statement s = copyThis.getStatement();
            Set<BlockIdentifier> b = copyThis.getBlockIdentifiers();
            if (!b.equals(expectedBlocks)) {
                return null;
            }

            if (s instanceof GotoStatement && copyThis.getTargets().contains(stm)) {
                s = new GotoStatement(BytecodeLoc.TODO);
            }
            // TODO: Assuming block identifiers here means we don't copy complex things.
            Op03SimpleStatement copy = new Op03SimpleStatement(caller.getBlockIdentifiers(), s.deepClone(cloneHelper), copyThis.getSSAIdentifiers(), idx);
            orig2copy.put(copyThis, copy);
            copies.add(copy);
            idx = idx.justAfter();
        }
        for (Op03SimpleStatement copyThis : copyThese) {
            List<Op03SimpleStatement> sources = copyThis.getSources();
            List<Op03SimpleStatement> targets = copyThis.getTargets();
            Op03SimpleStatement copy = orig2copy.get(copyThis);
            sources = copyST(sources, orig2copy, false);
            targets = copyST(targets, orig2copy, true);

            if (targets == null || sources == null) {
                return null;
            }
            copy.getSources().addAll(sources);
            copy.getTargets().addAll(targets);
            for (Op03SimpleStatement target : targets) {
                if (addSources.contains(target)) {
                    target.addSource(copy);
                }
            }
        }
        return copies;
    }

    private List<Op03SimpleStatement> copyST(List<Op03SimpleStatement> original, Map<Op03SimpleStatement, Op03SimpleStatement> replacements, boolean failIfMissing) {
        List<Op03SimpleStatement> res = ListFactory.newList();
        for (Op03SimpleStatement st : original) {
            Op03SimpleStatement repl = replacements.get(st);
            if (repl == null) {
                if (failIfMissing) {
                    return null;
                }
                continue;
            }
            res.add(repl);
        }
        return res;
    }
}
