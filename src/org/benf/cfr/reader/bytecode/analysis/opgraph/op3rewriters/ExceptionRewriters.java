package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StackValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CatchStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CommentStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.TryStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

public class ExceptionRewriters {

    static List<Op03SimpleStatement> eliminateCatchTemporaries(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> catches = Functional.filter(statements, new TypeFilter<CatchStatement>(CatchStatement.class));
        boolean effect = false;
        for (Op03SimpleStatement catchh : catches) {
            effect = effect | eliminateCatchTemporary(catchh);
        }
        if (effect) {
            // Before we identify finally, clean the code again.
            statements = Cleaner.removeUnreachableCode(statements, false);
        }
        return statements;
    }


    private static boolean eliminateCatchTemporary(Op03SimpleStatement catchh) {
        if (catchh.getTargets().size() != 1) return false;
        Op03SimpleStatement maybeAssign = catchh.getTargets().get(0);

        CatchStatement catchStatement = (CatchStatement) catchh.getStatement();
        LValue catching = catchStatement.getCreatedLValue();

        if (!(catching instanceof StackSSALabel)) return false;
        StackSSALabel catchingSSA = (StackSSALabel) catching;
        if (catchingSSA.getStackEntry().getUsageCount() != 1) return false;

        while (maybeAssign.getStatement() instanceof TryStatement) {
            // Note that the 'tried' path is always path 0 of a try statement.
            maybeAssign = maybeAssign.getTargets().get(0);
        }
        WildcardMatch match = new WildcardMatch();
        if (!match.match(new AssignmentSimple(match.getLValueWildCard("caught"), new StackValue(catchingSSA)),
                maybeAssign.getStatement())) {
            return false;
        }

        // Hurrah - maybeAssign is an assignment of the caught value.
        catchh.replaceStatement(new CatchStatement(catchStatement.getExceptions(), match.getLValueWildCard("caught").getMatch()));
        maybeAssign.nopOut();
        return true;
    }

    /* Basic principle with catch blocks - we mark all statements from the start
     * of a catch block, UNTIL they can be reached by something that isn't marked.
     *
     * Complexity comes from allowing back jumps inside a catch block.  If there's a BACK
     * JUMP
     * TODO : OrderCheat
     * which is not from a catchblock statement, we have to mark current location as
     * "last known guaranteed".  We then proceed, tentatively marking.
     *
     * As soon as we hit something which /can't/ be in the catch block, we can
     * unwind all tentatives which assume that it was.
     */
    static void identifyCatchBlocks(List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory) {
        List<Op03SimpleStatement> catchStarts = Functional.filter(in, new TypeFilter<CatchStatement>(CatchStatement.class));
        for (Op03SimpleStatement catchStart : catchStarts) {
            CatchStatement catchStatement = (CatchStatement) catchStart.getStatement();
            if (catchStatement.getCatchBlockIdent() == null) {
                BlockIdentifier blockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.CATCHBLOCK);
                catchStatement.setCatchBlockIdent(blockIdentifier);
                identifyCatchBlock(catchStart, blockIdentifier, in);
            }
        }
    }

    /*
     * Could be refactored out as uniquelyReachableFrom....
     */
    private static void identifyCatchBlock(Op03SimpleStatement start, BlockIdentifier blockIdentifier, List<Op03SimpleStatement> statements) {
        Set<Op03SimpleStatement> knownMembers = SetFactory.newSet();
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        seen.add(start);
        knownMembers.add(start);

        /*
         * Because a conditional will cause us to hit a parent which isn't in the block
         * if (a) goto x
         * ..
         * goto z
         * x:
         * ...
         * z
         *
         * (at z we'll see the parent from x before we've marked it)
         * , we have to make sure that we've exhausted pending possibilities before
         * we decide we're reachable from something outside the catch block.
         * (this is different to tentative marking, as there we're examining nodes
         * which are reachable from something we're not sure is actually in the block
         */
        LinkedList<Op03SimpleStatement> pendingPossibilities = ListFactory.newLinkedList();
        if (start.getTargets().size() != 1) {
            throw new ConfusedCFRException("Catch statement with multiple targets");
        }
        for (Op03SimpleStatement target : start.getTargets()) {
            pendingPossibilities.add(target);
            seen.add(target);
        }

        Map<Op03SimpleStatement, Set<Op03SimpleStatement>> allows = MapFactory.newLazyMap(new UnaryFunction<Op03SimpleStatement, Set<Op03SimpleStatement>>() {
            @Override
            public Set<Op03SimpleStatement> invoke(Op03SimpleStatement ignore) {
                return SetFactory.newSet();
            }
        });
        int sinceDefinite = 0;
        while (!pendingPossibilities.isEmpty() && sinceDefinite <= pendingPossibilities.size()) {
            Op03SimpleStatement maybe = pendingPossibilities.removeFirst();
            boolean definite = true;
            for (Op03SimpleStatement source : maybe.getSources()) {
                if (!knownMembers.contains(source)) {
                    /* If it's a backjump, we'll allow it.
                     * We won't tag the source as good, which means that we may have gaps
                     * if it turns out this was invalid.
                     */
                    if (!source.getIndex().isBackJumpTo(maybe)) {
                        definite = false;
                        allows.get(source).add(maybe);
                    }
                }
            }
            if (definite) {
                sinceDefinite = 0;
                // All of this guys sources are known
                knownMembers.add(maybe);
                Set<Op03SimpleStatement> allowedBy = allows.get(maybe);
                pendingPossibilities.addAll(allowedBy);
                // They'll get re-added if they're still blocked.
                allowedBy.clear();
                /* OrderCheat :
                 * only add backTargets which are to after the catch block started.
                 */
                for (Op03SimpleStatement target : maybe.getTargets()) {
                    // Don't need to check knownMembers, always included in seen.
                    if (!seen.contains(target)) {
                        seen.add(target);
                        if (target.getIndex().isBackJumpTo(start)) {
                            pendingPossibilities.add(target);
                        }
                    }
                }
            } else {
                /*
                 * Can't reach this one (or certainly, can't reach it given what we know yet)
                 */
                sinceDefinite++;
                pendingPossibilities.add(maybe);
            }
        }
        /*
         * knownMembers now defines the graph uniquely reachable from start.
         * TODO :
         * Now we have to check how well it lines up with the linear code assumption.
         */
        knownMembers.remove(start);
        if (knownMembers.isEmpty()) {
            List<Op03SimpleStatement> targets = start.getTargets();
            // actually already verified above, but I'm being paranoid.
            if (targets.size() != 1) throw new ConfusedCFRException("Synthetic catch block has multiple targets");
            knownMembers.add(insertBlockPadding("empty catch block", start, targets.get(0), blockIdentifier, statements));
        }
        /*
         * But now we have to remove (boo) non contiguous ones.
         * ORDERCHEAT.
         *
         * This is because otherwise we'll jump out, and back in to a block.
         *
         * Sort knownMembers
         */
        List<Op03SimpleStatement> knownMemberList = ListFactory.newList(knownMembers);
        Collections.sort(knownMemberList, new CompareByIndex());

        List<Op03SimpleStatement> truncatedKnownMembers = ListFactory.newList();
        int x = statements.indexOf(knownMemberList.get(0));
        List<Op03SimpleStatement> flushNops = ListFactory.newList();
        for (int l = statements.size(); x < l; ++x) {
            Op03SimpleStatement statement = statements.get(x);
            if (statement.isAgreedNop()) {
                flushNops.add(statement);
                continue;
            }
            if (!knownMembers.contains(statement)) break;
            truncatedKnownMembers.add(statement);
            if (!flushNops.isEmpty()) {
                truncatedKnownMembers.addAll(flushNops);
                flushNops.clear();
            }
        }

        for (Op03SimpleStatement inBlock : truncatedKnownMembers) {
            inBlock.getBlockIdentifiers().add(blockIdentifier);
        }
        /*
         * Find the (there should only be one) descendant of start.  It /SHOULD/ be the first sorted member of
         * known members, otherwise we have a problem.  Mark that as start of block.
         */
        Op03SimpleStatement first = start.getTargets().get(0);
        first.markFirstStatementInBlock(blockIdentifier);
    }

    // Up to now, try and catch blocks, while related, are treated in isolation.
    // We need to make sure they're logically grouped, so we can see when a block constraint is being violated.
    static void combineTryCatchBlocks(List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tries = getTries(in);
        for (Op03SimpleStatement tryStatement : tries) {
            combineTryCatchBlocks(tryStatement);
        }
    }

    private static List<Op03SimpleStatement> getTries(List<Op03SimpleStatement> in) {
        return Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
    }


    private static void combineTryCatchBlocks(final Op03SimpleStatement tryStatement) {
        Set<Op03SimpleStatement> allStatements = SetFactory.newSet();
        TryStatement innerTryStatement = (TryStatement) tryStatement.getStatement();

        allStatements.addAll(Misc.GraphVisitorBlockReachable.getBlockReachable(tryStatement, innerTryStatement.getBlockIdentifier()));

        // all in block, reachable
        for (Op03SimpleStatement target : tryStatement.getTargets()) {
            if (target.getStatement() instanceof CatchStatement) {
                CatchStatement catchStatement = (CatchStatement) target.getStatement();
                allStatements.addAll(Misc.GraphVisitorBlockReachable.getBlockReachable(target, catchStatement.getCatchBlockIdent()));
            }
        }

        /* Add something inFRONT of the try statement which is NOT going to be in this block, which can adopt it
         * (This is obviously an unreal artifact)
         */
        /* See Tock test for why we should only extend try/catch.
         */
        Set<BlockIdentifier> tryBlocks = tryStatement.getBlockIdentifiers();
        tryBlocks = SetFactory.newSet(Functional.filter(tryBlocks, new Predicate<BlockIdentifier>() {
            @Override
            public boolean test(BlockIdentifier in) {
                return in.getBlockType() == BlockType.TRYBLOCK || in.getBlockType() == BlockType.CATCHBLOCK;
            }
        }));
        if (tryBlocks.isEmpty()) return;

        List<Op03SimpleStatement> orderedStatements = ListFactory.newList(allStatements);
        Collections.sort(orderedStatements, new CompareByIndex(false));

        for (Op03SimpleStatement statement : orderedStatements) {
            for (BlockIdentifier ident : tryBlocks) {
                if (!statement.getBlockIdentifiers().contains(ident) &&
                        statement.getSources().contains(statement.getLinearlyPrevious()) &&
                        statement.getLinearlyPrevious().getBlockIdentifiers().contains(ident)) {
                    statement.addPossibleExitFor(ident);
                }
            }
            statement.getBlockIdentifiers().addAll(tryBlocks);
        }
    }


    /*
     * The Op4 -> Structured op4 transform requires blocks to have a member, in order to trigger the parent being claimed.
     * We may need to add synthetic block entries.
     *
     * Because there is an assumption that all statements are in 'statements'
     * todo : remove this assumption!
     * we need to link it to the end.
     */
    private static Op03SimpleStatement insertBlockPadding(@SuppressWarnings("SameParameterValue") String comment, Op03SimpleStatement insertAfter, Op03SimpleStatement insertBefore, BlockIdentifier blockIdentifier, List<Op03SimpleStatement> statements) {
        Op03SimpleStatement between = new Op03SimpleStatement(insertAfter.getBlockIdentifiers(), new CommentStatement(comment), insertAfter.getIndex().justAfter());
        insertAfter.replaceTarget(insertBefore, between);
        insertBefore.replaceSource(insertAfter, between);
        between.addSource(insertAfter);
        between.addTarget(insertBefore);
        between.getBlockIdentifiers().add(blockIdentifier);
        statements.add(between);
        return between;
    }

    /*
     * If we've got any code between try and catch blocks, see if it can legitimately be moved
     * after the catch block.
     * (com/db4o/internal/Config4Class)
     *
     * For each try statement with one handler(*), find code between the end of the try and the start of the
     * handler.  If this is finally code, we should have picked that up by now.
     *
     * If that code starts/ends in the same blockset as the catch target/try source, and the catch block doesn't assume
     * it can fall through, then move the code after the catch block.
     *
     * This will be handled in a more general way by the op04 code, but doing it early gives us a better chance to spot
     * some issues.
     *
     */
    static void extractExceptionMiddle(List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tryStatements = Functional.filter(in, new ExactTypeFilter<TryStatement>(TryStatement.class));
        if (tryStatements.isEmpty()) return;
        Collections.reverse(tryStatements);
        for (Op03SimpleStatement tryStatement : tryStatements) {
            if (tryStatement.getTargets().size() != 2) continue;
            SingleExceptionAddressing trycatch = getSingleTryCatch(tryStatement, in);
            if (trycatch == null) continue;
            if (extractExceptionMiddle(tryStatement, in, trycatch)) {
                // We will only have ever moved something downwards, and won't have removed any tries, so this doesn't
                // invalidate any loop invariants.
                Cleaner.sortAndRenumberInPlace(in);
                trycatch.tryBlock.reindex(in);
                trycatch.catchBlock.reindex(in);
            }
            extractCatchEnd(in, trycatch);
        }
    }

    // If a try statement doesn't have a body at all, it could be elided.
    // (as could the catch, if the try is the only source).
    public static void handleEmptyTries(List<Op03SimpleStatement> in) {
        Map<BlockIdentifier, Op03SimpleStatement> firstByBlock = null;
        boolean effect = false;

        List<Op03SimpleStatement> tries = getTries(in);
        for (Op03SimpleStatement tryStatement : tries) {
            BlockIdentifier block = ((TryStatement)tryStatement.getStatement()).getBlockIdentifier();
            Op03SimpleStatement tgtStm = tryStatement.getTargets().get(0);
            if (tgtStm.getBlockIdentifiers().contains(block)) {
                continue;
            }
            // Ok, it doesn't contain it.  We now have to check if that block is used anywhere :(
            if (firstByBlock == null) {
                firstByBlock = getFirstByBlock(in);
            }
            if (firstByBlock.containsKey(block)) {
                continue;
            }
            // Either remove altogether (not implemented yet), or insert a spurious statement
            Op03SimpleStatement newStm = new Op03SimpleStatement(tryStatement.getBlockIdentifiers(),
                    new CommentStatement("empty try"), tryStatement.getIndex().justAfter());
            newStm.getBlockIdentifiers().add(block);
            newStm.addSource(tryStatement);
            newStm.addTarget(tgtStm);
            tgtStm.replaceSource(tryStatement, newStm);
            tryStatement.replaceTarget(tgtStm, newStm);
            effect = true;
        }

        if (effect) {
            Cleaner.sortAndRenumberInPlace(in);
        }
    }

    private static Map<BlockIdentifier, Op03SimpleStatement> getFirstByBlock(List<Op03SimpleStatement> in) {
        Map<BlockIdentifier, Op03SimpleStatement> res = MapFactory.newMap();
        for (Op03SimpleStatement stm : in) {
            for (BlockIdentifier i : stm.getBlockIdentifiers()) {
                // probe then insert (eww) but we'll only be inserting once per block.
                if (!res.containsKey(i)) {
                    res.put(i, stm);
                }
            }
        }
        return res;
    }

    private static class SingleExceptionAddressing {
        BlockIdentifier tryBlockIdent;
        BlockIdentifier catchBlockIdent;
        LinearScannedBlock tryBlock;
        LinearScannedBlock catchBlock;

        private SingleExceptionAddressing(BlockIdentifier tryBlockIdent, BlockIdentifier catchBlockIdent, LinearScannedBlock tryBlock, LinearScannedBlock catchBlock) {
            this.tryBlockIdent = tryBlockIdent;
            this.catchBlockIdent = catchBlockIdent;
            this.tryBlock = tryBlock;
            this.catchBlock = catchBlock;
        }
    }

    private static SingleExceptionAddressing getSingleTryCatch(Op03SimpleStatement trystm, List<Op03SimpleStatement> statements) {
        int idx = statements.indexOf(trystm);
        TryStatement tryStatement = (TryStatement)trystm.getStatement();
        BlockIdentifier tryBlockIdent = tryStatement.getBlockIdentifier();
        LinearScannedBlock tryBlock = getLinearScannedBlock(statements, idx, trystm, tryBlockIdent, true);
        if (tryBlock == null) return null;
        Op03SimpleStatement catchs = trystm.getTargets().get(1);
        Statement testCatch = catchs.getStatement();
        if (!(testCatch instanceof CatchStatement)) return null;
        CatchStatement catchStatement = (CatchStatement)testCatch;
        BlockIdentifier catchBlockIdent = catchStatement.getCatchBlockIdent();
        LinearScannedBlock catchBlock = getLinearScannedBlock(statements, statements.indexOf(catchs), catchs, catchBlockIdent, true);
        if (catchBlock == null) return null;

        if (!catchBlock.isAfter(tryBlock)) return null;

        return new SingleExceptionAddressing(tryBlockIdent, catchBlockIdent, tryBlock, catchBlock);
    }

    private static boolean extractExceptionMiddle(Op03SimpleStatement trystm, List<Op03SimpleStatement> statements, SingleExceptionAddressing trycatch) {
        LinearScannedBlock tryBlock = trycatch.tryBlock;
        LinearScannedBlock catchBlock = trycatch.catchBlock;
        BlockIdentifier tryBlockIdent = trycatch.tryBlockIdent;
        BlockIdentifier catchBlockIdent = trycatch.catchBlockIdent;

        /*
         * Check that the catch block does not exit to the statement linearly after it.
         * (if there is such a statement).
         */
        int catchLast = catchBlock.getIdxLast();
        if (catchLast < statements.size()-1) {
            Op03SimpleStatement afterCatchBlock = statements.get(catchLast+1);
            for (Op03SimpleStatement source : afterCatchBlock.getSources()) {
                if (source.getBlockIdentifiers().contains(catchBlockIdent)) return false;
            }
        }

        if (catchBlock.immediatelyFollows(tryBlock)) return false;

        Set<BlockIdentifier> expected = trystm.getBlockIdentifiers();
        /*
         * Ok, we have a try block, a catch block and something inbetween them.  Verify that there are no jumps INTO
         * this intermediate code other than from the try or catch block, (or blocks in this range)
         * and that the blockset of the START of the try block is present the whole time.
         */
        Set<Op03SimpleStatement> middle = SetFactory.newSet();
        List<Op03SimpleStatement> toMove = ListFactory.newList();
        for (int x=tryBlock.getIdxLast()+1;x<catchBlock.getIdxFirst();++x) {
            Op03SimpleStatement stm = statements.get(x);
            middle.add(stm);
            toMove.add(stm);
        }
        for (int x=tryBlock.getIdxLast()+1;x<catchBlock.getIdxFirst();++x) {
            Op03SimpleStatement stm = statements.get(x);
            if (!stm.getBlockIdentifiers().containsAll(expected)) {
                return false;
            }
            for (Op03SimpleStatement source : stm.getSources()) {
                if (source.getIndex().isBackJumpTo(stm)) {
                    Set<BlockIdentifier> sourceBlocks = source.getBlockIdentifiers();
                    if (!(sourceBlocks.contains(tryBlockIdent) || (sourceBlocks.contains(catchBlockIdent)))) {
                        return false;
                    }
                }
            }
        }
        InstrIndex afterIdx = catchBlock.getLast().getIndex().justAfter();
        for (Op03SimpleStatement move : toMove) {
            move.setIndex(afterIdx);
            afterIdx = afterIdx.justAfter();
        }
        return true;
    }



    /*
     * If the try block jumps directly into the catch block, we might have an over-aggressive catch statement,
     * where the last bit should be outside it.
     *
     * As a conservative heuristic, treat this as valid if there are no other out of block forward jumps in the try
     * block. (strictly speaking this is pessimistic, and avoids indexed breaks and continues.  Revisit if examples
     * of those are found to be problematic).
     */
    private static void extractCatchEnd(List<Op03SimpleStatement> statements, SingleExceptionAddressing trycatch) {
        LinearScannedBlock tryBlock = trycatch.tryBlock;
        BlockIdentifier tryBlockIdent = trycatch.tryBlockIdent;
        BlockIdentifier catchBlockIdent = trycatch.catchBlockIdent;
        Op03SimpleStatement possibleAfterBlock = null;
        /*
         * If there IS a statement after the catch block, it can't be jumped to by the try block.
         * Otherwise, the catch block is the last code in the method.
         */
        if (trycatch.catchBlock.getIdxLast() < statements.size()-1) {
            Op03SimpleStatement afterCatch = statements.get(trycatch.catchBlock.getIdxLast()+1);
            for (Op03SimpleStatement source : afterCatch.getSources()) {
                if (source.getBlockIdentifiers().contains(tryBlockIdent)) return;
            }
        }
        for (int x = tryBlock.getIdxFirst()+1; x <= tryBlock.getIdxLast(); ++x) {
            List<Op03SimpleStatement> targets = statements.get(x).getTargets();
            for (Op03SimpleStatement target : targets) {
                if (target.getBlockIdentifiers().contains(catchBlockIdent)) {
                    if (possibleAfterBlock == null) {
                        possibleAfterBlock = target;
                    } else {
                        if (target != possibleAfterBlock) return;
                    }
                }
            }
        }
        if (possibleAfterBlock == null) return;

        /*
         * We require that possibleAfterBlock's block identifiers are the same as the try block, plus the catch ident.
         */
        Set<BlockIdentifier> tryStartBlocks = trycatch.tryBlock.getFirst().getBlockIdentifiers();
        Set<BlockIdentifier> possibleBlocks = possibleAfterBlock.getBlockIdentifiers();
        if (possibleBlocks.size() != tryStartBlocks.size()+1) return;

        if (!possibleBlocks.containsAll(tryStartBlocks)) return;
        if (!possibleBlocks.contains(catchBlockIdent)) return;

        /* We require that the reachable statements IN THE CATCH BLOCK are exactly the ones which are between
         * possibleAfterBlock and the end of the catch block.
         */
        int possibleIdx = statements.indexOf(possibleAfterBlock);
        LinearScannedBlock unmarkBlock = getLinearScannedBlock(statements, possibleIdx, possibleAfterBlock, catchBlockIdent, false);
        if (unmarkBlock == null) return;
        for (int x = unmarkBlock.getIdxFirst(); x<=unmarkBlock.getIdxLast(); ++x) {
            statements.get(x).getBlockIdentifiers().remove(catchBlockIdent);
        }
    }

    private static LinearScannedBlock getLinearScannedBlock(List<Op03SimpleStatement> statements, int idx, Op03SimpleStatement stm, BlockIdentifier blockIdentifier, boolean prefix) {
        Set<Op03SimpleStatement> found = SetFactory.newSet();
        int nextIdx = idx+(prefix?1:0);
        if (prefix) found.add(stm);
        int cnt = statements.size();
        do {
            Op03SimpleStatement nstm = statements.get(nextIdx);
            if (!nstm.getBlockIdentifiers().contains(blockIdentifier)) break;
            found.add(nstm);
            nextIdx++;
        } while (nextIdx < cnt);
        Set<Op03SimpleStatement> reachable = Misc.GraphVisitorBlockReachable.getBlockReachable(stm, blockIdentifier);
        if (!reachable.equals(found)) return null;
        nextIdx--;
        if (reachable.isEmpty()) return null;
        return new LinearScannedBlock(stm, statements.get(nextIdx), idx, nextIdx);
    }

}
