package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;

import java.util.List;
import java.util.Set;

public class PushThroughGoto {

    /*
     * If we have
     *
     * if (.... ) goto x
     * FFFFFF
     * goto Y
     *
     * and the instruction BEFORE Y does not have Y as its direct predecessor, we can push FFF through.
     *
     * Why do we want to do this?  Because if X is directly after Y, we might get to the point where we end up as
     *
     * if (..... ) goto x
     * goto y
     * X
     *
     * Which can be converted into a negative jump.
     *
     * We only do this for linear statements, we'd need a structured transform to do something better.
     * (at op4 stage).
     */
    public static List<Op03SimpleStatement> pushThroughGoto(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> pathtests = Functional.filter(statements, new ExactTypeFilter<GotoStatement>(GotoStatement.class));
        boolean success = false;
        for (Op03SimpleStatement gotostm : pathtests) {
            if (gotostm.getTargets().get(0).getIndex().isBackJumpTo(gotostm)) {
                if (pushThroughGoto(gotostm, statements)) {
                    success = true;
                }
            }
        }
        if (success) {
            statements = Cleaner.sortAndRenumber(statements);
            // This is being done twice deliberately.  Should rewrite rewriteNegativeJumps to iterate.
            // in practice 2ce is fine.
            // see /com/db4o/internal/btree/BTreeNode.class
            Op03Rewriters.rewriteNegativeJumps(statements, false);
            Op03Rewriters.rewriteNegativeJumps(statements, false);
        }
        return statements;
    }


    private static boolean pushThroughGoto(Op03SimpleStatement forwardGoto, List<Op03SimpleStatement> statements) {

        if (forwardGoto.getSources().size() != 1) return false;

        final Op03SimpleStatement tgt = forwardGoto.getTargets().get(0);
        int idx = statements.indexOf(tgt);
        if (idx == 0) return false;
        final Op03SimpleStatement before = statements.get(idx - 1);
        // TODO : should be simple to verify that the first test is uneccessary.
        if (tgt.getSources().contains(before)) return false;
        if (tgt.getSources().size() != 1) return false;

        InstrIndex beforeTgt = tgt.getIndex().justBefore();

        /*
         * We can't push through a goto if TGT is the first instruction after a loop body.
         */
        class IsLoopBlock implements Predicate<BlockIdentifier> {
            @Override
            public boolean test(BlockIdentifier in) {
                BlockType blockType = in.getBlockType();
                switch (blockType) {
                    case WHILELOOP:
                    case DOLOOP:
                        return true;
                }
                return false;
            }
        }
        IsLoopBlock isLoopBlock = new IsLoopBlock();
        Set<BlockIdentifier> beforeLoopBlocks = SetFactory.newSet(Functional.filterSet(before.getBlockIdentifiers(), isLoopBlock));
        Set<BlockIdentifier> tgtLoopBlocks = SetFactory.newSet(Functional.filterSet(tgt.getBlockIdentifiers(), isLoopBlock));
        if (!beforeLoopBlocks.equals(tgtLoopBlocks)) return false;

        class IsExceptionBlock implements Predicate<BlockIdentifier> {
            @Override
            public boolean test(BlockIdentifier in) {
                BlockType blockType = in.getBlockType();
                switch (blockType) {
                    case TRYBLOCK:
                    case SWITCH:
                    case CATCHBLOCK:
                    case CASE:
                        return true;
                }
                return false;
            }
        }
        Predicate<BlockIdentifier> exceptionFilter = new IsExceptionBlock();

        Set<BlockIdentifier> exceptionBlocks = SetFactory.newSet(Functional.filterSet(tgt.getBlockIdentifiers(), exceptionFilter));
        int nextCandidateIdx = statements.indexOf(forwardGoto) - 1;

        Op03SimpleStatement lastTarget = tgt;
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        boolean success = false;
        while (true) {
            Op03SimpleStatement tryMoveThis = forwardGoto.getSources().get(0);

            if (!moveable(tryMoveThis.getStatement())) return success;
            if (!seen.add(tryMoveThis)) return success;

            if (statements.get(nextCandidateIdx) != tryMoveThis) return success;
            if (tryMoveThis.getTargets().size() != 1) return success;
            // If sources > 1, we can't continue processing after this one, but we can do this one.
            boolean abortNext = (tryMoveThis.getSources().size() != 1);
            // Is it in the same exception blocks?
            Set<BlockIdentifier> moveEB = SetFactory.newSet(Functional.filterSet(tryMoveThis.getBlockIdentifiers(), exceptionFilter));
            if (!moveEB.equals(exceptionBlocks)) return success;
            /* Move this instruction through the goto
             */
            forwardGoto.getSources().clear();
            for (Op03SimpleStatement beforeTryMove : tryMoveThis.getSources()) {
                beforeTryMove.replaceTarget(tryMoveThis, forwardGoto);
                forwardGoto.getSources().add(beforeTryMove);
            }
            tryMoveThis.getSources().clear();
            tryMoveThis.getSources().add(forwardGoto);
            forwardGoto.replaceTarget(lastTarget, tryMoveThis);
            tryMoveThis.replaceTarget(forwardGoto, lastTarget);
            lastTarget.replaceSource(forwardGoto, tryMoveThis);

            tryMoveThis.setIndex(beforeTgt);
            beforeTgt = beforeTgt.justBefore();

            tryMoveThis.getBlockIdentifiers().clear();
            tryMoveThis.getBlockIdentifiers().addAll(lastTarget.getBlockIdentifiers());
            lastTarget = tryMoveThis;
            nextCandidateIdx--;
            success = true;
            if (abortNext) return true;
        }
    }

    private static boolean moveable(Statement statement) {
        Class<?> clazz = statement.getClass();
        if (clazz == Nop.class) return true;
        if (clazz == AssignmentSimple.class) return true;
        if (clazz == CommentStatement.class) return true;
        if (clazz == ExpressionStatement.class) return true;
        if (clazz == IfExitingStatement.class) return true;
        return false;
    }

}
