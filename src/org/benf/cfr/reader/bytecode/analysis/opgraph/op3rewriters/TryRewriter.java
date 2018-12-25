package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheckImpl;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.collections.SetUtil;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

class TryRewriter {

    /*
     * This is a terrible order cheat.
     */
    private static void extendTryBlock(Op03SimpleStatement tryStatement, List<Op03SimpleStatement> in, DCCommonState dcCommonState) {
        TryStatement tryStatementInner = (TryStatement) tryStatement.getStatement();
        BlockIdentifier tryBlockIdent = tryStatementInner.getBlockIdentifier();

        Op03SimpleStatement lastStatement = null;
        Op03SimpleStatement currentStatement = tryStatement.getTargets().get(0);
        int x = in.indexOf(currentStatement);

        List<Op03SimpleStatement> jumps = ListFactory.newList();

        while (currentStatement.getBlockIdentifiers().contains(tryBlockIdent)) {
            ++x;
            if (x >= in.size()) {
                return;
            }
            lastStatement = currentStatement;
            if (currentStatement.getStatement() instanceof JumpingStatement) {
                jumps.add(currentStatement);
            }
            currentStatement = in.get(x);
        }

        /*
         * Get the types of all caught expressions.  Anything we can't understand resolves to runtime expression
         * This allows us to extend exception blocks if they're catching checked exceptions, and we can tell that no
         * checked exceptions could be thrown.
         */
        Set<JavaRefTypeInstance> caught = SetFactory.newSet();
        List<Op03SimpleStatement> targets = tryStatement.getTargets();
        for (int i = 1, len = targets.size(); i < len; ++i) {
            Statement statement = targets.get(i).getStatement();
            if (!(statement instanceof CatchStatement)) continue;
            CatchStatement catchStatement = (CatchStatement) statement;
            List<ExceptionGroup.Entry> exceptions = catchStatement.getExceptions();
            for (ExceptionGroup.Entry entry : exceptions) {
                caught.add(entry.getCatchType());
            }
        }

        ExceptionCheck exceptionCheck = new ExceptionCheckImpl(dcCommonState, caught);

        mainloop:
        while (!currentStatement.getStatement().canThrow(exceptionCheck)) {
            Set<BlockIdentifier> validBlocks = SetFactory.newSet();
            validBlocks.add(tryBlockIdent);
            for (int i = 1, len = tryStatement.getTargets().size(); i < len; ++i) {
                Op03SimpleStatement tgt = tryStatement.getTargets().get(i);
                Statement tgtStatement = tgt.getStatement();
                if (tgtStatement instanceof CatchStatement) {
                    validBlocks.add(((CatchStatement) tgtStatement).getCatchBlockIdent());
                } else if (tgtStatement instanceof FinallyStatement) {
                    validBlocks.add(((FinallyStatement) tgtStatement).getFinallyBlockIdent());
                } else {
                    return;
                }
            }

            boolean foundSource = false;
            for (Op03SimpleStatement source : currentStatement.getSources()) {
                if (!SetUtil.hasIntersection(validBlocks, source.getBlockIdentifiers())) return;
                if (source.getBlockIdentifiers().contains(tryBlockIdent)) foundSource = true;
            }

            if (!foundSource) return;
            /*
             * If this statement is a return statement, in the same blocks as JUST THE try (i.e. it hasn't fallen
             * into a catch etc), we can assume it belonged in the try.
             */
            currentStatement.getBlockIdentifiers().add(tryBlockIdent);

            x++;
            if (x >= in.size()) break;
            Op03SimpleStatement nextStatement = in.get(x);
            if (!currentStatement.getTargets().contains(nextStatement)) {
                // Then ALL of nextStatements sources must ALREADY be in the block.
                for (Op03SimpleStatement source : nextStatement.getSources()) {
                    if (!source.getBlockIdentifiers().contains(tryBlockIdent)) break mainloop;
                }
            }
            lastStatement = currentStatement;
            if (currentStatement.getStatement() instanceof JumpingStatement) {
                jumps.add(currentStatement);
            }
            currentStatement = nextStatement;
        }
        if (lastStatement != null &&  lastStatement.getTargets().isEmpty()) {
            // We have opportunity to rescan and see if there is a UNIQUE forward jump out.
            Set<Op03SimpleStatement> outTargets = SetFactory.newSet();
            for (Op03SimpleStatement jump : jumps) {
                JumpingStatement jumpingStatement = (JumpingStatement)jump.getStatement();
                // This is ugly.  I'm sure I do it elsewhere.   Refactor.
                int idx = jumpingStatement.isConditional() ? 1 : 0;
                if (idx >= jump.getTargets().size()) return; // can't happen.
                Op03SimpleStatement jumpTarget = jump.getTargets().get(idx);
                if (jumpTarget.getIndex().isBackJumpFrom(jump)) continue;
                if (jumpTarget.getBlockIdentifiers().contains(tryBlockIdent)) continue;
                outTargets.add(jumpTarget);
            }
            if (outTargets.size() == 1) {
                Op03SimpleStatement replace = outTargets.iterator().next();
                Op03SimpleStatement newJump = new Op03SimpleStatement(lastStatement.getBlockIdentifiers(), new GotoStatement(), lastStatement.getIndex().justAfter());
                newJump.addTarget(replace);
                replace.addSource(newJump);
                for (Op03SimpleStatement jump : jumps) {
                    if (jump.getTargets().contains(replace)) {
                        jump.replaceTarget(replace, newJump);
                        newJump.addSource(jump);
                        replace.removeSource(jump);
                    }
                }
                in.add(in.indexOf(lastStatement)+1, newJump);
            }
        }
    }

    static void extendTryBlocks(DCCommonState dcCommonState, List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tries = Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement tryStatement : tries) {
            extendTryBlock(tryStatement, in, dcCommonState);
        }
    }

    static void combineTryCatchEnds(List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tries = Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement tryStatement : tries) {
            combineTryCatchEnds(tryStatement, in);
        }
    }

    /*
     * Find the last statement in the block, assuming that this statement is the one BEFORE, linearly.
     */
    private static Op03SimpleStatement getLastContiguousBlockStatement(BlockIdentifier blockIdentifier, List<Op03SimpleStatement> in, Op03SimpleStatement preBlock) {
        if (preBlock.getTargets().isEmpty()) return null;
        Op03SimpleStatement currentStatement = preBlock.getTargets().get(0);
        int x = in.indexOf(currentStatement);

        if (!currentStatement.getBlockIdentifiers().contains(blockIdentifier)) return null;

        Op03SimpleStatement last = currentStatement;
        while (currentStatement.getBlockIdentifiers().contains(blockIdentifier)) {
            ++x;
            if (x >= in.size()) {
                break;
            }
            last = currentStatement;
            currentStatement = in.get(x);
        }
        return last;
    }

    private static void combineTryCatchEnds(Op03SimpleStatement tryStatement, List<Op03SimpleStatement> in) {
        TryStatement innerTryStatement = (TryStatement) tryStatement.getStatement();
        List<Op03SimpleStatement> lastStatements = ListFactory.newList();
        lastStatements.add(getLastContiguousBlockStatement(innerTryStatement.getBlockIdentifier(), in, tryStatement));
        for (int x = 1, len = tryStatement.getTargets().size(); x < len; ++x) {
            Op03SimpleStatement statementContainer = tryStatement.getTargets().get(x);
            Statement statement = statementContainer.getStatement();
            if (statement instanceof CatchStatement) {
                lastStatements.add(getLastContiguousBlockStatement(((CatchStatement) statement).getCatchBlockIdent(), in, statementContainer));
            } else if (statement instanceof FinallyStatement) {
                return;
//                lastStatements.add(getLastContiguousBlockStatement(((FinallyStatement) statement).getFinallyBlockIdent(), in, statementContainer));
            } else {
                return;
            }
        }
        if (lastStatements.size() <= 1) return;
        for (Op03SimpleStatement last : lastStatements) {
            if (last == null) return;
            if (last.getStatement().getClass() != GotoStatement.class) {
                return;
            }
        }
        Op03SimpleStatement target = lastStatements.get(0).getTargets().get(0);
        for (Op03SimpleStatement last : lastStatements) {
            if (last.getTargets().get(0) != target) return;
        }
        // Insert a fake target after the final one.
        Op03SimpleStatement finalStatement = lastStatements.get(lastStatements.size() - 1);
        int beforeTgt = in.indexOf(finalStatement);
        Op03SimpleStatement proxy = new Op03SimpleStatement(tryStatement.getBlockIdentifiers(), new GotoStatement(), finalStatement.getIndex().justAfter());
        in.add(beforeTgt + 1, proxy);
        proxy.addTarget(target);
        target.addSource(proxy);

        // Handle duplicates - is there a neater way? (Avoiding filter pass).
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        for (Op03SimpleStatement last : lastStatements) {
            if (!seen.add(last)) continue;
            GotoStatement gotoStatement = (GotoStatement) last.getStatement();
            gotoStatement.setJumpType(JumpType.END_BLOCK);
            last.replaceTarget(target, proxy);
            target.removeSource(last);
            proxy.addSource(last);
        }
    }

    /*
     * Convert:
     *
     * try {
     *   ...
     *   goto x
     * } catch (...) {
     *   // either block ending in goto x, or returning block.
     *   // essentially, either no forward exits, or last instruction is a forward exit to x.
     * }
     *
     * to
     * try {
     *   ...
     *   goto r; // (goto-out-of-try)
     * } catch (...) {
     *   ///
     * }
     * r:
     * goto x << REDIRECT
     *
     *
     */
    private static void extractExceptionJumps(Op03SimpleStatement tryi, List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tryTargets = tryi.getTargets();
        /*
         * Require that at least one block ends in a forward jump to the same block depth as tryi,
         * and that all others are either the same, or do not have a terminal forward jump.
         */
        Op03SimpleStatement uniqueForwardTarget = null;
        Set<BlockIdentifier> relevantBlocks = SetFactory.newSet();
        Op03SimpleStatement lastEnd = null;
        int lpidx = 0;
        for (Op03SimpleStatement tgt : tryTargets) {
            BlockIdentifier block = getBlockStart(((lpidx++ == 0) ? tryi : tgt).getStatement());
            if (block == null) return;
            relevantBlocks.add(block);
            Op03SimpleStatement lastStatement = getLastContiguousBlockStatement(block, in, tgt);
            if (lastStatement == null) return;
            if (lastStatement.getStatement().getClass() == GotoStatement.class) {
                Op03SimpleStatement lastTgt = lastStatement.getTargets().get(0);
                if (uniqueForwardTarget == null) {
                    uniqueForwardTarget = lastTgt;
                } else if (uniqueForwardTarget != lastTgt) return;
            }
            lastEnd = lastStatement;
        }
        if (uniqueForwardTarget == null) return;
        /*
         * We require that uniqueForwardTarget is in the same blocks as the original try
         * instruction.
         */
        if (!uniqueForwardTarget.getBlockIdentifiers().equals(tryi.getBlockIdentifiers())) return;

        /*
         * Find the instruction linearly after the final block.
         * If this is == uniqueForwardTarget, fine, mark those jumps as jumps out of try, and leave.
         * Otherwise, make sure this doesn't have any sources IN relevantBlocks, and place REDIRECT here.
         */
        int idx = in.indexOf(lastEnd);
        if (idx >= in.size() - 1) return;
        Op03SimpleStatement next = in.get(idx + 1);
        if (next == uniqueForwardTarget) {
            return;
            // handle.
        }
        for (Op03SimpleStatement source : next.getSources()) {
            if (SetUtil.hasIntersection(source.getBlockIdentifiers(), relevantBlocks)) {
                // Can't handle.
                return;
            }
        }
        List<Op03SimpleStatement> blockSources = ListFactory.newLinkedList();
        for (Op03SimpleStatement source : uniqueForwardTarget.getSources()) {
            if (SetUtil.hasIntersection(source.getBlockIdentifiers(), relevantBlocks)) {
                blockSources.add(source);
            }
        }
        Op03SimpleStatement indirect = new Op03SimpleStatement(next.getBlockIdentifiers(), new GotoStatement(), next.getIndex().justBefore());
        for (Op03SimpleStatement source : blockSources) {
            Statement srcStatement = source.getStatement();
            if (srcStatement instanceof GotoStatement) {
                ((GotoStatement) srcStatement).setJumpType(JumpType.GOTO_OUT_OF_TRY);
            }
            uniqueForwardTarget.removeSource(source);
            source.replaceTarget(uniqueForwardTarget, indirect);
            indirect.addSource(source);
        }
        indirect.addTarget(uniqueForwardTarget);
        uniqueForwardTarget.addSource(indirect);
        in.add(idx + 1, indirect);
    }


    private static BlockIdentifier getBlockStart(Statement statement) {
        Class<?> clazz = statement.getClass();
        if (clazz == TryStatement.class) {
            TryStatement tryStatement = (TryStatement) statement;
            return tryStatement.getBlockIdentifier();
        } else if (clazz == CatchStatement.class) {
            CatchStatement catchStatement = (CatchStatement) statement;
            return catchStatement.getCatchBlockIdent();
        } else if (clazz == FinallyStatement.class) {
            FinallyStatement finallyStatement = (FinallyStatement) statement;
            return finallyStatement.getFinallyBlockIdent();
        }
        return null;
    }

    static void extractExceptionJumps(List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tries = Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement tryi : tries) {
            extractExceptionJumps(tryi, in);
        }
    }

    private static void rewriteTryBackJump(Op03SimpleStatement stm) {
        InstrIndex idx = stm.getIndex();
        TryStatement tryStatement = (TryStatement) stm.getStatement();
        Op03SimpleStatement firstbody = stm.getTargets().get(0);
        BlockIdentifier blockIdentifier = tryStatement.getBlockIdentifier();
        Iterator<Op03SimpleStatement> sourceIter = stm.getSources().iterator();
        while (sourceIter.hasNext()) {
            Op03SimpleStatement source = sourceIter.next();
            if (idx.isBackJumpFrom(source)) {
                if (source.getBlockIdentifiers().contains(blockIdentifier)) {
                    source.replaceTarget(stm, firstbody);
                    firstbody.addSource(source);
                    sourceIter.remove(); // remove source inline.
                }
            }
        }
    }

    /*
     * If there's a backjump INSIDE a try block which points to the beginning of the try block, move it to the next
     * instruction.
     */
    static void rewriteTryBackJumps(List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tries = Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement trystm : tries) {
            rewriteTryBackJump(trystm);
        }
    }

}
