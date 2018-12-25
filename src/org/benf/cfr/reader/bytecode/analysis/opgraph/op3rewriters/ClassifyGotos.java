package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CatchStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.JumpingStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.TryStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.collections.*;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClassifyGotos {
    public static void classifyGotos(List<Op03SimpleStatement> in) {
        List<Pair<Op03SimpleStatement, Integer>> gotos = ListFactory.newList();
        Map<BlockIdentifier, Op03SimpleStatement> tryStatementsByBlock = MapFactory.newMap();
        Map<BlockIdentifier, List<BlockIdentifier>> catchStatementsByBlock = MapFactory.newMap();
        Map<BlockIdentifier, Set<BlockIdentifier>> catchToTries = MapFactory.newLazyMap(new UnaryFunction<BlockIdentifier, Set<BlockIdentifier>>() {
            @Override
            public Set<BlockIdentifier> invoke(BlockIdentifier arg) {
                return SetFactory.newOrderedSet();
            }
        });
        for (int x = 0, len = in.size(); x < len; ++x) {
            Op03SimpleStatement stm = in.get(x);
            Statement statement = stm.getStatement();
            Class<?> clz = statement.getClass();
            if (clz == TryStatement.class) {
                TryStatement tryStatement = (TryStatement) statement;
                BlockIdentifier tryBlockIdent = tryStatement.getBlockIdentifier();
                tryStatementsByBlock.put(tryBlockIdent, stm);
                List<Op03SimpleStatement> targets = stm.getTargets();
                List<BlockIdentifier> catchBlocks = ListFactory.newList();
                catchStatementsByBlock.put(tryStatement.getBlockIdentifier(), catchBlocks);
                for (int y = 1, len2 = targets.size(); y < len2; ++y) {
                    Statement statement2 = targets.get(y).getStatement();
                    if (statement2.getClass() == CatchStatement.class) {
                        BlockIdentifier catchBlockIdent = ((CatchStatement) statement2).getCatchBlockIdent();
                        catchBlocks.add(catchBlockIdent);
                        catchToTries.get(catchBlockIdent).add(tryBlockIdent);
                    }
                }
            } else if (clz == GotoStatement.class) {
                GotoStatement gotoStatement = (GotoStatement) statement;
                if (gotoStatement.getJumpType().isUnknown()) {
                    gotos.add(Pair.make(stm, x));
                }
            }
        }
        /*
         * Pass over try statements.  If there aren't any, don't bother.
         */
        if (!tryStatementsByBlock.isEmpty()) {
            for (Pair<Op03SimpleStatement, Integer> goto_ : gotos) {
                Op03SimpleStatement stm = goto_.getFirst();
                int idx = goto_.getSecond();
                if (classifyTryLeaveGoto(stm, idx, tryStatementsByBlock.keySet(), tryStatementsByBlock, catchStatementsByBlock, in)) {
                    continue;
                }
                classifyCatchLeaveGoto(stm, idx, tryStatementsByBlock.keySet(), tryStatementsByBlock, catchStatementsByBlock, catchToTries, in);
            }
        }
    }

    private static boolean classifyTryLeaveGoto(Op03SimpleStatement gotoStm, int idx, Set<BlockIdentifier> tryBlockIdents, Map<BlockIdentifier, Op03SimpleStatement> tryStatementsByBlock, Map<BlockIdentifier, List<BlockIdentifier>> catchStatementByBlock, List<Op03SimpleStatement> in) {
        Set<BlockIdentifier> blocks = gotoStm.getBlockIdentifiers();
        return classifyTryCatchLeaveGoto(gotoStm, blocks, idx, tryBlockIdents, tryStatementsByBlock, catchStatementByBlock, in);
    }

    private static void classifyCatchLeaveGoto(Op03SimpleStatement gotoStm, int idx, Set<BlockIdentifier> tryBlockIdents, Map<BlockIdentifier, Op03SimpleStatement> tryStatementsByBlock, Map<BlockIdentifier, List<BlockIdentifier>> catchStatementByBlock, Map<BlockIdentifier, Set<BlockIdentifier>> catchBlockToTryBlocks, List<Op03SimpleStatement> in) {
        Set<BlockIdentifier> inBlocks = gotoStm.getBlockIdentifiers();

        /*
         * Map blocks to the union of the TRY blocks we're in catch blocks of.
         */
        Set<BlockIdentifier> blocks = SetFactory.newOrderedSet();
        for (BlockIdentifier block : inBlocks) {
            //
            // In case it's a lazy map, 2 stage lookup and fetch.
            if (catchBlockToTryBlocks.containsKey(block)) {
                Set<BlockIdentifier> catchToTries = catchBlockToTryBlocks.get(block);
                blocks.addAll(catchToTries);
            }
        }

        classifyTryCatchLeaveGoto(gotoStm, blocks, idx, tryBlockIdents, tryStatementsByBlock, catchStatementByBlock, in);
    }


    /*
     * Attempt to determine if a goto is jumping over catch blocks - if it is, we can mark it as a GOTO_OUT_OF_TRY
     * (the same holds for a goto inside a catch, we use the same marker).
     */
    private static boolean classifyTryCatchLeaveGoto(Op03SimpleStatement gotoStm, Set<BlockIdentifier> blocks, int idx, Set<BlockIdentifier> tryBlockIdents, Map<BlockIdentifier, Op03SimpleStatement> tryStatementsByBlock, Map<BlockIdentifier, List<BlockIdentifier>> catchStatementByBlock, List<Op03SimpleStatement> in) {
        if (idx >= in.size() - 1) return false;

        GotoStatement gotoStatement = (GotoStatement) gotoStm.getStatement();

        Set<BlockIdentifier> tryBlocks = SetUtil.intersectionOrNull(blocks, tryBlockIdents);
        if (tryBlocks == null) return false;


        Op03SimpleStatement after = in.get(idx + 1);
        Set<BlockIdentifier> afterBlocks = SetUtil.intersectionOrNull(after.getBlockIdentifiers(), tryBlockIdents);

        if (afterBlocks != null) tryBlocks.removeAll(afterBlocks);
        if (tryBlocks.size() != 1) return false;
        BlockIdentifier left = tryBlocks.iterator().next();

        // Ok, so we've jumped out of exactly one try block.  But where have we jumped to?  Is it to directly after
        // a catch block for that try block?
        Op03SimpleStatement tryStatement = tryStatementsByBlock.get(left);
        if (tryStatement == null) return false;

        List<BlockIdentifier> catchForThis = catchStatementByBlock.get(left);
        if (catchForThis == null) return false;

        /*
         * We require that gotoStm's one target is
         * /not in 'left'/
         * just after a catch block.
         * Not in any of the catch blocks.
         */
        Op03SimpleStatement gotoTgt = gotoStm.getTargets().get(0);
        Set<BlockIdentifier> gotoTgtIdents = gotoTgt.getBlockIdentifiers();
        if (SetUtil.hasIntersection(gotoTgtIdents, catchForThis)) return false;
        int idxtgt = in.indexOf(gotoTgt);
        if (idxtgt == 0) return false;
        Op03SimpleStatement prev = in.get(idxtgt - 1);
        if (!SetUtil.hasIntersection(prev.getBlockIdentifiers(), catchForThis)) return false;
        // YAY!
        gotoStatement.setJumpType(JumpType.GOTO_OUT_OF_TRY);
        return true;
    }


    public static void classifyAnonymousBlockGotos(List<Op03SimpleStatement> in, boolean agressive) {
        int agressiveOffset = agressive ? 1 : 0;

        /*
         * Now, finally, for each unclassified goto, see if we can mark it as a break out of an anonymous block.
         */
        for (Op03SimpleStatement statement : in) {
            Statement inner = statement.getStatement();
            if (inner instanceof JumpingStatement) {
                JumpingStatement jumpingStatement = (JumpingStatement) inner;
                JumpType jumpType = jumpingStatement.getJumpType();
                if (jumpType != JumpType.GOTO) continue;
                Op03SimpleStatement targetStatement = (Op03SimpleStatement) jumpingStatement.getJumpTarget().getContainer();
                boolean isForwardJump = targetStatement.getIndex().isBackJumpTo(statement);
                if (isForwardJump) {
                    Set<BlockIdentifier> targetBlocks = targetStatement.getBlockIdentifiers();
                    Set<BlockIdentifier> srcBlocks = statement.getBlockIdentifiers();
                    if (targetBlocks.size() < srcBlocks.size() + agressiveOffset  && srcBlocks.containsAll(targetBlocks)) {
                        /*
                         * Remove all the switch blocks from srcBlocks.
                         */
                        srcBlocks = Functional.filterSet(srcBlocks, new Predicate<BlockIdentifier>() {
                            @Override
                            public boolean test(BlockIdentifier in) {
                                BlockType blockType = in.getBlockType();
                                if (blockType == BlockType.CASE) return false;
                                if (blockType == BlockType.SWITCH) return false;
                                return true;
                            }
                        });
                        if (targetBlocks.size() < srcBlocks.size() + agressiveOffset && srcBlocks.containsAll(targetBlocks)) {
                            /*
                             * Break out of an anonymous block
                             */
                            /*
                             * Should we now be re-looking at ALL other forward jumps to this target?
                             */
                            jumpingStatement.setJumpType(JumpType.BREAK_ANONYMOUS);
                        }
                    }
                }
            }
        }

    }



}
