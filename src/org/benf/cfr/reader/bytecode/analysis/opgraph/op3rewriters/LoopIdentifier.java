package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

public class LoopIdentifier {


    private static class LoopResult {
        final BlockIdentifier blockIdentifier;
        final Op03SimpleStatement blockStart;

        private LoopResult(BlockIdentifier blockIdentifier, Op03SimpleStatement blockStart) {
            this.blockIdentifier = blockIdentifier;
            this.blockStart = blockStart;
        }
    }

    // Find simple loops.
    // Identify distinct set of backjumps (b1,b2), which jump back to somewhere (p) which has a forward
    // jump to somewhere which is NOT a /DIRECT/ parent of the backjumps (i.e. has to go through p)
    // p must be a direct parent of all of (b1,b2)
    public static void identifyLoops1(Method method, List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory) {
        // Find back references.
        // Verify that they belong to jump instructions (otherwise something has gone wrong)
        // (if, goto).

        List<Op03SimpleStatement> pathtests = Functional.filter(statements, new TypeFilter<GotoStatement>(GotoStatement.class));
        for (Op03SimpleStatement start : pathtests) {
            considerAsPathologicalLoop(start, statements);
        }

        List<Op03SimpleStatement> backjumps = Functional.filter(statements, new Misc.HasBackJump());
        List<Op03SimpleStatement> starts = Functional.uniqAll(Functional.map(backjumps, new Misc.GetBackJump()));
        /* Each of starts is the target of a back jump.
         * Consider each of these seperately, and for each of these verify
         * that it contains a forward jump to something which is not a parent except through p.
         */
        Map<BlockIdentifier, Op03SimpleStatement> blockEndsCache = MapFactory.newMap();
        Collections.sort(starts, new CompareByIndex());

        List<LoopResult> loopResults = ListFactory.newList();
        Set<BlockIdentifier> relevantBlocks = SetFactory.newSet();
        for (Op03SimpleStatement start : starts) {
            BlockIdentifier blockIdentifier = considerAsWhileLoopStart(method, start, statements, blockIdentifierFactory, blockEndsCache);
            if (blockIdentifier == null) {
                blockIdentifier = considerAsDoLoopStart(start, statements, blockIdentifierFactory, blockEndsCache);
            }
            if (blockIdentifier != null) {
                loopResults.add(new LoopResult(blockIdentifier, start));
                relevantBlocks.add(blockIdentifier);
            }
        }

        if (loopResults.isEmpty()) return;
        Collections.reverse(loopResults);
        /*
         * If we have any overlapping but not nested loops, that's because the earlier one(s)
         * are not properly exited, just continued over (see LoopTest48).
         *
         * Need to extend loop bodies and transform whiles into continues.
         */
        fixLoopOverlaps(statements, loopResults, relevantBlocks);
    }


    /* For each block, if the start is inside other blocks, but the last backjump is
     * NOT, then we need to convert the last backjump into a continue / conditional continue,
     * and add a while (true). :P
     */
    private static void fixLoopOverlaps(List<Op03SimpleStatement> statements, List<LoopResult> loopResults, Set<BlockIdentifier> relevantBlocks) {

        Map<BlockIdentifier, List<BlockIdentifier>> requiredExtents = MapFactory.newLazyMap(new UnaryFunction<BlockIdentifier, List<BlockIdentifier>>() {
            @Override
            public List<BlockIdentifier> invoke(BlockIdentifier arg) {
                return ListFactory.newList();
            }
        });

        Map<BlockIdentifier, Op03SimpleStatement> lastForBlock = MapFactory.newMap();

        for (LoopResult loopResult : loopResults) {
            final Op03SimpleStatement start = loopResult.blockStart;
            final BlockIdentifier testBlockIdentifier = loopResult.blockIdentifier;

            Set<BlockIdentifier> startIn = SetUtil.intersectionOrNull(start.getBlockIdentifiers(), relevantBlocks);
            List<Op03SimpleStatement> backSources = Functional.filter(start.getSources(), new Predicate<Op03SimpleStatement>() {
                @Override
                public boolean test(Op03SimpleStatement in) {
                    return in.getBlockIdentifiers().contains(testBlockIdentifier) &&
                            in.getIndex().isBackJumpTo(start);
                }
            });

            if (backSources.isEmpty()) continue;
            Collections.sort(backSources, new CompareByIndex());
            Op03SimpleStatement lastBackSource = backSources.get(backSources.size() - 1);
            /*
             * If start is in a relevantBlock that an end isn't, then THAT BLOCK needs to be extended to at least the end
             * of testBlockIdentifier.
             */
            lastForBlock.put(testBlockIdentifier, lastBackSource);
            if (startIn == null) continue;

            Set<BlockIdentifier> backIn = SetUtil.intersectionOrNull(lastBackSource.getBlockIdentifiers(), relevantBlocks);
            if (backIn == null) continue;
            if (!backIn.containsAll(startIn)) {
                // NB Not ordered - will this bite me?  Shouldn't.
                Set<BlockIdentifier> startMissing = SetFactory.newSet(startIn);
                startMissing.removeAll(backIn);
                for (BlockIdentifier missing : startMissing) {
                    requiredExtents.get(missing).add(testBlockIdentifier);
                }
            }
        }

        if (requiredExtents.isEmpty()) return;

        // RequiredExtents[key] should be extended to the last of VALUE.
        List<BlockIdentifier> extendBlocks = ListFactory.newList(requiredExtents.keySet());
        Collections.sort(extendBlocks, new Comparator<BlockIdentifier>() {
            @Override
            public int compare(BlockIdentifier blockIdentifier, BlockIdentifier blockIdentifier2) {
                return blockIdentifier.getIndex() - blockIdentifier2.getIndex();  // reverse order.
            }
        });

        Comparator<Op03SimpleStatement> comparator = new CompareByIndex();

        // NB : we're deliberately not using key ordering.
        for (BlockIdentifier extendThis : extendBlocks) {
            List<BlockIdentifier> possibleEnds = requiredExtents.get(extendThis);
            if (possibleEnds.isEmpty()) continue;
            // Find last block.
            // We re-fetch because we might have updated the possibleEndOps.
            List<Op03SimpleStatement> possibleEndOps = ListFactory.newList();
            for (BlockIdentifier end : possibleEnds) {
                possibleEndOps.add(lastForBlock.get(end));
            }
            Collections.sort(possibleEndOps, comparator);

            Op03SimpleStatement extendTo = possibleEndOps.get(possibleEndOps.size() - 1);
            /*
             * Need to extend block 'extendThis' to 'extendTo'.
             * We also need to rewrite any terminal backjumps as continues, and
             * add a 'while true' (or block to that effect).
             */
            Op03SimpleStatement oldEnd = lastForBlock.get(extendThis);

            int start = statements.indexOf(oldEnd);
            int end = statements.indexOf(extendTo);

            for (int x = start; x <= end; ++x) {
                statements.get(x).getBlockIdentifiers().add(extendThis);
            }
            // Rewrite oldEnd appropriately.  Leave end of block dangling, we will fix it later.
            rewriteEndLoopOverlapStatement(oldEnd, extendThis);
        }
    }


    private static void rewriteEndLoopOverlapStatement(Op03SimpleStatement oldEnd, BlockIdentifier loopBlock) {
        Statement statement = oldEnd.getStatement();
        Class<?> clazz = statement.getClass();
        if (clazz == WhileStatement.class) {
            WhileStatement whileStatement = (WhileStatement) statement;
            ConditionalExpression condition = whileStatement.getCondition();
            if (oldEnd.getTargets().size() == 2) {
                IfStatement repl = new IfStatement(condition);
                repl.setKnownBlocks(loopBlock, null);
                repl.setJumpType(JumpType.CONTINUE);
                oldEnd.replaceStatement(repl);
                if (oldEnd.getThisComparisonBlock() == loopBlock) {
                    oldEnd.clearThisComparisonBlock();
                }
                return;
            } else if (oldEnd.getTargets().size() == 1 && condition == null) {
                GotoStatement repl = new GotoStatement();
                repl.setJumpType(JumpType.CONTINUE);
                oldEnd.replaceStatement(repl);
                if (oldEnd.getThisComparisonBlock() == loopBlock) {
                    oldEnd.clearThisComparisonBlock();
                }
                return;
            }
        }
    }


    /*
 * To handle special case tricksiness.
 */
    private static boolean considerAsPathologicalLoop(final Op03SimpleStatement start, List<Op03SimpleStatement> statements) {
        if (start.getStatement().getClass() != GotoStatement.class) return false;
        if (start.getTargets().get(0) != start) return false;
        Op03SimpleStatement next = new Op03SimpleStatement(start.getBlockIdentifiers(), new GotoStatement(), start.getIndex().justAfter());
        start.replaceStatement(new CommentStatement("Infinite loop"));
        start.replaceTarget(start, next);
        start.replaceSource(start, next);
        next.addSource(start);
        next.addTarget(start);
        statements.add(statements.indexOf(start) + 1, next);
        return true;
    }

    private static BlockIdentifier considerAsDoLoopStart(final Op03SimpleStatement start, final List<Op03SimpleStatement> statements,
                                                         BlockIdentifierFactory blockIdentifierFactory,
                                                         Map<BlockIdentifier, Op03SimpleStatement> postBlockCache) {

        final InstrIndex startIndex = start.getIndex();
        List<Op03SimpleStatement> backJumpSources = start.getSources();
        if (backJumpSources.isEmpty()) {
            throw new ConfusedCFRException("Node doesn't have ANY sources! " + start);
        }
        backJumpSources = Functional.filter(backJumpSources, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getIndex().compareTo(startIndex) >= 0;
            }
        });
        Collections.sort(backJumpSources, new CompareByIndex());
        if (backJumpSources.isEmpty()) {
            throw new ConfusedCFRException("Node should have back jump sources.");
        }
        final int lastJumpIdx = backJumpSources.size() - 1;
        Op03SimpleStatement lastJump = backJumpSources.get(lastJumpIdx);
        boolean conditional = false;
        boolean wasConditional = false;
        if (lastJump.getStatement() instanceof IfStatement) {
            conditional = true;
            wasConditional = true;
            IfStatement ifStatement = (IfStatement) lastJump.getStatement();
            if (ifStatement.getJumpTarget().getContainer() != start) {
                return null;
            }
            /*
             * But, if there are other back jumps to the start inside, which are either unconditional or don't have the same
             * condition, we can't do this....
             */
            for (int x=0;x<lastJumpIdx;++x) {
                Op03SimpleStatement prevJump = backJumpSources.get(x);
                Statement prevJumpStatement = prevJump.getStatement();
                if (prevJumpStatement.getClass() == GotoStatement.class) {
                    conditional = false;
                    break;
                }
            }
        }
//        if (!conditional) return false;

        int startIdx = statements.indexOf(start);
        int endIdx = statements.indexOf(lastJump);

        if (startIdx >= endIdx) return null;

        BlockIdentifier blockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(conditional ? BlockType.DOLOOP : BlockType.UNCONDITIONALDOLOOP);

        /* Given that the potential statements inside this block are idxConditional+1 -> idxAfterEnd-1, [a->b]
        * there SHOULD be a prefix set (or all) in here which is addressable from idxConditional+1 without leaving the
        * range [a->b].  Determine this.  If we have reachable entries which aren't in the prefix, we can't cope.
        */
        try {
            validateAndAssignLoopIdentifier(statements, startIdx, endIdx + 1, blockIdentifier, start);
        } catch (CannotPerformDecode e) {
            // Can't perform this optimisation.
            return null;
        }


        // Add a 'do' statement infront of the block (which does not belong to the block)
        // transform the test to a 'POST_WHILE' statement.
        Op03SimpleStatement doStatement = new Op03SimpleStatement(start.getBlockIdentifiers(), new DoStatement(blockIdentifier), start.getIndex().justBefore());
        doStatement.getBlockIdentifiers().remove(blockIdentifier);
        // we need to link the do statement in between all the sources of start WHICH
        // are NOT in blockIdentifier.
        List<Op03SimpleStatement> startSources = ListFactory.newList(start.getSources());
        for (Op03SimpleStatement source : startSources) {
            if (!source.getBlockIdentifiers().contains(blockIdentifier)) {
                source.replaceTarget(start, doStatement);
                start.removeSource(source);
                doStatement.addSource(source);
            }
        }
        doStatement.addTarget(start);
        start.addSource(doStatement);
        Op03SimpleStatement postBlock;
        if (conditional) {
            postBlock = lastJump.getTargets().get(0);
        } else {
            /*
             * The best we can do is know it's a fall through to whatever WOULD have happened.
             */

            if (wasConditional) {
                // Negate the test, turn it into a break.  Insert the unconditional between the condition
                // and what used to be its fallthrough.
                IfStatement ifStatement = (IfStatement)lastJump.getStatement();
                ifStatement.negateCondition();
                ifStatement.setJumpType(JumpType.BREAK);
                Op03SimpleStatement oldFallthrough = lastJump.getTargets().get(0);
                Op03SimpleStatement oldTaken = lastJump.getTargets().get(1);
                // Now, lastJump EXPLICTLY has to jump to tgt1 (was fallthrough), and falls through to GOTO tgt2 (was jumps to tgt2).
                Op03SimpleStatement newBackJump = new Op03SimpleStatement(lastJump.getBlockIdentifiers(), new GotoStatement(), lastJump.getIndex().justAfter());
                // ULGY - need primitive operator!
                lastJump.getTargets().set(0, newBackJump);
                lastJump.getTargets().set(1, oldFallthrough);
                oldTaken.replaceSource(lastJump, newBackJump);
                newBackJump.addSource(lastJump);
                newBackJump.addTarget(oldTaken);
                statements.add(statements.indexOf(oldFallthrough), newBackJump);
                lastJump = newBackJump;
            }

            int newIdx = statements.indexOf(lastJump) + 1;

            if (newIdx >= statements.size()) {
                postBlock = new Op03SimpleStatement(SetFactory.<BlockIdentifier>newSet(), new ReturnNothingStatement(), lastJump.getIndex().justAfter());
                statements.add(postBlock);

//                return false;
//                throw new ConfusedCFRException("Unconditional while with break but no following statement.");
            } else {
                postBlock = statements.get(newIdx);
            }
        }

        if (start.getFirstStatementInThisBlock() != null) {
            /* We need to figure out if this new loop is inside or outside block started at start.
             *
             */
            BlockIdentifier outer = Misc.findOuterBlock(start.getFirstStatementInThisBlock(), blockIdentifier, statements);
            if (blockIdentifier == outer) {
                // Ok, we're the new outer
                throw new UnsupportedOperationException();
            } else {
                // we're the new inner.  We need to change start to be first in US, and make US first of what start was
                // in.
                doStatement.setFirstStatementInThisBlock(start.getFirstStatementInThisBlock());
                start.setFirstStatementInThisBlock(blockIdentifier);
            }
        }

        /*
         * One final very grotty 'optimisation' - if there are any try blocks that began INSIDE the
         * loop (so present in the last statement, but not in the first), shuffle the lastJump until it
         * is after them.
         *
         * This (seems) to only apply to unconditionals.
         */
        shuntLoop:
        if (!conditional) {
            Set<BlockIdentifier> lastContent = SetFactory.newSet(lastJump.getBlockIdentifiers());
            lastContent.removeAll(start.getBlockIdentifiers());
            Set<BlockIdentifier> internalTryBlocks = SetFactory.newOrderedSet(Functional.filterSet(lastContent, new Predicate<BlockIdentifier>() {
                @Override
                public boolean test(BlockIdentifier in) {
                    return in.getBlockType() == BlockType.TRYBLOCK;
                }
            }));
            // internalTryBlocks represents try blocks which started AFTER the loop did.
            if (internalTryBlocks.isEmpty()) break shuntLoop;

            final int postBlockIdx = statements.indexOf(postBlock);
            int lastPostBlock = postBlockIdx;
            innerShutLoop:
            do {
                if (lastPostBlock + 1 >= statements.size()) break innerShutLoop;

                int currentIdx = lastPostBlock + 1;
                Op03SimpleStatement stm = statements.get(lastPostBlock);
                if (!(stm.getStatement() instanceof CatchStatement)) break innerShutLoop;

                CatchStatement catchStatement = (CatchStatement) stm.getStatement();
                BlockIdentifier catchBlockIdent = catchStatement.getCatchBlockIdent();
                List<BlockIdentifier> tryBlocks = Functional.map(catchStatement.getExceptions(), new UnaryFunction<ExceptionGroup.Entry, BlockIdentifier>() {
                    @Override
                    public BlockIdentifier invoke(ExceptionGroup.Entry arg) {
                        return arg.getTryBlockIdentifier();
                    }
                });
                if (!internalTryBlocks.containsAll(tryBlocks)) break innerShutLoop;
                while (currentIdx < statements.size() - 1 && statements.get(currentIdx).getBlockIdentifiers().contains(catchBlockIdent)) {
                    currentIdx++;
                }
                lastPostBlock = currentIdx;
            } while (true);
            if (lastPostBlock != postBlockIdx) {
                // We will have rewritten a COMPLETELY impossible loop - but we're already too far gone.
                if (!lastJump.getTargets().contains(start)) {
                    throw new ConfusedCFRException("Nonsensical loop would be emitted - failure");
                }
                final Op03SimpleStatement afterNewJump = statements.get(lastPostBlock);
                // Find after statement.  Insert a jump forward (out) here, and then insert
                // a synthetic lastJump.  The previous lastJump should now jump to our synthetic
                // We can insert a BACK jump to lastJump's target
                //
                Op03SimpleStatement newBackJump = new Op03SimpleStatement(afterNewJump.getBlockIdentifiers(), new GotoStatement(), afterNewJump.getIndex().justBefore());
                newBackJump.addTarget(start);
                newBackJump.addSource(lastJump);
                lastJump.replaceTarget(start, newBackJump);
                start.replaceSource(lastJump, newBackJump);
                /*
                 * If the instruction we're being placed infront of has a direct precedent, then that needs to get transformed
                 * into a goto (actually a break, but that will happen later).  Otherwise, it will be fine.
                 */
                Op03SimpleStatement preNewJump = statements.get(lastPostBlock - 1);
                if (afterNewJump.getSources().contains(preNewJump)) {
                    Op03SimpleStatement interstit = new Op03SimpleStatement(preNewJump.getBlockIdentifiers(), new GotoStatement(), newBackJump.getIndex().justBefore());
                    preNewJump.replaceTarget(afterNewJump, interstit);
                    afterNewJump.replaceSource(preNewJump, interstit);
                    interstit.addSource(preNewJump);
                    interstit.addTarget(afterNewJump);
                    statements.add(lastPostBlock, interstit);
                    lastPostBlock++;
                }

                statements.add(lastPostBlock, newBackJump);
                lastJump = newBackJump;
                postBlock = afterNewJump;
                /*
                 * Now mark everything we just walked into the loop body.
                 */
                for (int idx = postBlockIdx; idx <= lastPostBlock; ++idx) {
                    statements.get(idx).markBlock(blockIdentifier);
                }
            }
        }

        statements.add(statements.indexOf(start), doStatement);
        lastJump.markBlockStatement(blockIdentifier, null, lastJump, statements);
        start.markFirstStatementInBlock(blockIdentifier);


        postBlockCache.put(blockIdentifier, postBlock);

        return blockIdentifier;
    }

    /* Is the first conditional jump NOT one of the sources of start?
* Take the target of the first conditional jump - is it somehwhere which is not reachable from
* any of the forward sources of start without going through start?
*
* If so we've probably got a for/while loop.....
* decode both as a while loop, we can convert it into a for later.
*/
    private static BlockIdentifier considerAsWhileLoopStart(final Method method,
                                                            final Op03SimpleStatement start, final List<Op03SimpleStatement> statements,
                                                            BlockIdentifierFactory blockIdentifierFactory,
                                                            Map<BlockIdentifier, Op03SimpleStatement> postBlockCache) {
        final InstrIndex startIndex = start.getIndex();
        List<Op03SimpleStatement> backJumpSources = start.getSources();
        backJumpSources = Functional.filter(backJumpSources, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getIndex().compareTo(startIndex) >= 0;
            }
        });
        Collections.sort(backJumpSources, new CompareByIndex());
        Op03SimpleStatement conditional = findFirstConditional(start);
        if (conditional == null) {
            // No conditional before we have a branch?  Probably a do { } while.
            return null;
        }
        // Now we've found our first conditional before a branch - is the target AFTER the last backJump?
        // Requires Debuggered conditionals.
        // TODO : ORDERING
        Op03SimpleStatement lastJump = backJumpSources.get(backJumpSources.size() - 1);
        /* Conditional has 2 targets - one of which has to NOT be a parent of 'sources', unless
         * it involves going through conditional the other way.
         */
        List<Op03SimpleStatement> conditionalTargets = conditional.getTargets();
        /*
         * This could be broken by an obfuscator easily.  We need a transform state which
         * normalises the code so the jump out is the explicit jump.
         * TODO : Could do this by finding which one of the targets of the condition is NOT reachable
         * TODO : by going back from each of the backJumpSources to conditional
         *
         * TODO: This might give us something WAY past the end of the loop, if the next instruction is to
         * jump past a catch block.....
         */
        Op03SimpleStatement loopBreak = conditionalTargets.get(1);

        /*
         * One very special case here - if the conditional is an EXPLICIT self-loop.
         * In which case the taken branch isn't the exit, it's the loop.
         *
         * Rewrote as
         * x :  if (cond) goto x+2
         * x+1 : goto x
         * x+2
         *
         */
        if (loopBreak == conditional && start == conditional) {
            Op03SimpleStatement backJump = new Op03SimpleStatement(conditional.getBlockIdentifiers(), new GotoStatement(), conditional.getIndex().justAfter());
            Op03SimpleStatement notTaken = conditional.getTargets().get(0);
            conditional.replaceTarget(notTaken, backJump);
            conditional.replaceSource(conditional, backJump);
            conditional.replaceTarget(conditional, notTaken);
            backJump.addSource(conditional);
            backJump.addTarget(conditional);
            statements.add(statements.indexOf(conditional) + 1, backJump);
            conditionalTargets = conditional.getTargets();
            loopBreak = notTaken;
        }

        if (loopBreak.getIndex().compareTo(lastJump.getIndex()) <= 0) {
            // The conditional doesn't take us to after the last back jump, i.e. it's not a while {} loop.
            // ... unless it's an inner while loop continuing to a prior loop.
            if (loopBreak.getIndex().compareTo(startIndex) >= 0) {
                return null;
            }
        }

        if (start != conditional) {
            // We'll have problems - there are actions taken inside the conditional.
            return null;
        }
        int idxConditional = statements.indexOf(start);

        /* If this loop has a test at the bottom, we may have a continue style exit, i.e. the loopBreak
         * is not just reachable from the top.  We can find this by seeing if loopBreak is reachable from
         * any of the backJumpSources, without going through start.
         *
         * OR we may just have a do { } while....
         */
        /* Take the statement which directly preceeds loopbreak
         * TODO : ORDERCHEAT
         * and verify that it's reachable from conditional, WITHOUT going through start.
         * If so, we guess that it's the end of the loop.
         */
        int idxAfterEnd = statements.indexOf(loopBreak);
        if (idxAfterEnd < idxConditional) {
            /*
             * We've got an inner loop which is terminating back to the start of the outer loop.
             * This means we have to figure out the body of the loop by considering back jumps.
             * We can't rely on the last statement in the loop being a backjump to the start, as it
             * may be a continue/break to an outer loop.
             */
            /* We probably need a while block between start and the END of the loop which begins at idxEnd.
             * (if that exists.)
             */
            Op03SimpleStatement startOfOuterLoop = statements.get(idxAfterEnd);
            if (startOfOuterLoop.getThisComparisonBlock() == null) {
                // Boned.
                return null;
            }
            // Find the END of this block.
            Op03SimpleStatement endOfOuter = postBlockCache.get(startOfOuterLoop.getThisComparisonBlock());
            if (endOfOuter == null) {
                throw new ConfusedCFRException("BlockIdentifier doesn't exist in blockEndsCache");
            }
            idxAfterEnd = statements.indexOf(endOfOuter);
        }

        /* TODO : ORDERCHEAT */
        // Mark instructions in the list between start and maybeEndLoop as being in this block.
        if (idxConditional >= idxAfterEnd) {
//            throw new ConfusedCFRException("Can't decode block");
            return null;
        }
        BlockIdentifier blockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.WHILELOOP);

        /* Given that the potential statements inside this block are idxConditional+1 -> idxAfterEnd-1, [a->b]
        * there SHOULD be a prefix set (or all) in here which is addressable from idxConditional+1 without leaving the
        * range [a->b].  Determine this.  If we have reachable entries which aren't in the prefix, we can't cope.
        */
        int lastIdx;
        try {
            lastIdx = validateAndAssignLoopIdentifier(statements, idxConditional + 1, idxAfterEnd, blockIdentifier, start);
        } catch (CannotPerformDecode e) {
            return null;
        }

        Op03SimpleStatement lastInBlock = statements.get(lastIdx);
        Op03SimpleStatement blockEnd = statements.get(idxAfterEnd);
        //
        start.markBlockStatement(blockIdentifier, lastInBlock, blockEnd, statements);
        statements.get(idxConditional + 1).markFirstStatementInBlock(blockIdentifier);
        postBlockCache.put(blockIdentifier, blockEnd);
        /*
         * is the end of the while loop jumping to something which is NOT directly after it?  If so, we need to introduce
         * an intermediate jump.
         */
        Op03SimpleStatement afterLastInBlock = (lastIdx + 1) < statements.size() ? statements.get(lastIdx + 1) : null;
        loopBreak = conditional.getTargets().get(1);
        if (afterLastInBlock != loopBreak) {
            Op03SimpleStatement newAfterLast = new Op03SimpleStatement(afterLastInBlock.getBlockIdentifiers(), new GotoStatement(), lastInBlock.getIndex().justAfter());
            conditional.replaceTarget(loopBreak, newAfterLast);
            newAfterLast.addSource(conditional);
            loopBreak.replaceSource(conditional, newAfterLast);
            newAfterLast.addTarget(loopBreak);
            statements.add(newAfterLast);
        }

        return blockIdentifier;
    }


    private static Op03SimpleStatement findFirstConditional(Op03SimpleStatement start) {
        Set<Op03SimpleStatement> visited = SetFactory.newSet();
        do {
            Statement innerStatement = start.getStatement();
            if (innerStatement instanceof IfStatement) {
                return start;
            }
            List<Op03SimpleStatement> targets = start.getTargets();
            if (targets.size() != 1) return null;
            start = targets.get(0);
            if (visited.contains(start)) {
                return null;
            }
            visited.add(start);
        } while (start != null);
        return null;
    }





    private static int validateAndAssignLoopIdentifier(List<Op03SimpleStatement> statements, int idxTestStart, int idxAfterEnd, BlockIdentifier blockIdentifier, Op03SimpleStatement start) {
        int last = Misc.getFarthestReachableInRange(statements, idxTestStart, idxAfterEnd);

        /*
         * What if the last back jump was inside a catch statement?  Find catch statements which exist at
         * last, but not in start - we have to extend the loop to the end of the catch statements....
         * and change it to a while (false).
         */
        Op03SimpleStatement discoveredLast = statements.get(last);
        Set<BlockIdentifier> lastBlocks = SetFactory.newSet(discoveredLast.getBlockIdentifiers());
        lastBlocks.removeAll(start.getBlockIdentifiers());
        Set<BlockIdentifier> catches = SetFactory.newSet(Functional.filterSet(lastBlocks, new Predicate<BlockIdentifier>() {
            @Override
            public boolean test(BlockIdentifier in) {
                BlockType type = in.getBlockType();
                return type == BlockType.CATCHBLOCK ||
                       type == BlockType.SWITCH;
            }
        }));
        int newlast = last;
        while (!catches.isEmpty()) {
            /*
             * Need to find the rest of these catch blocks, and add them to the range.
             */
            Op03SimpleStatement stm = statements.get(newlast);
            catches.retainAll(stm.getBlockIdentifiers());
            if (catches.isEmpty()) {
                break;
            }
            last = newlast;
            if (newlast < statements.size() - 1) {
                newlast++;
            } else {
                break;
            }
        }


        for (int x = idxTestStart; x <= last; ++x) {
            statements.get(x).markBlock(blockIdentifier);
        }
        return last;
    }

}
