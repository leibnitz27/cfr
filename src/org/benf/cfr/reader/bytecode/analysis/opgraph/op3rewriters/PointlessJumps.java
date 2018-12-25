package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.JumpingStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.TryStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.util.collections.SetUtil;

import java.util.List;
import java.util.Set;

public class PointlessJumps {
    /* Remove pointless jumps
     *
     * Normalise code by removing jumps which have been introduced to confuse.
     */
    public static void removePointlessJumps(List<Op03SimpleStatement> statements) {

        /*
         * Odd first pass, but we want to translate
         *
         * a : goto x
         * b : goto x
         *
         * into
         *
         * a : comment falls through to b
         * b : goto x
         */
        int size = statements.size() - 1;
        for (int x = 0; x < size - 1; ++x) {
            Op03SimpleStatement a = statements.get(x);
            Op03SimpleStatement b = statements.get(x + 1);
            if (a.getStatement().getClass() == GotoStatement.class &&
                    b.getStatement().getClass() == GotoStatement.class &&
                    a.getTargets().get(0) == b.getTargets().get(0) &&
//                    a.getJumpType() != JumpType.BREAK
                    a.getBlockIdentifiers().equals(b.getBlockIdentifiers())
            ) {
                Op03SimpleStatement realTgt = a.getTargets().get(0);
                realTgt.removeSource(a);
                a.replaceTarget(realTgt, b);
                b.addSource(a);
                a.nopOut();
            }
        }


        // Do this pass first, as it needs spatial locality.
        for (int x = 0; x < size-1; ++x) {
            Op03SimpleStatement maybeJump = statements.get(x);
            if (maybeJump.getStatement().getClass() == GotoStatement.class &&
                    maybeJump.getJumpType() != JumpType.BREAK &&
                    maybeJump.getTargets().size() == 1 &&
                    maybeJump.getTargets().get(0) == statements.get(x + 1)) {
                // But only if they're in the same blockset!
                if (maybeJump.getBlockIdentifiers().equals(statements.get(x+1).getBlockIdentifiers())) {
                    maybeJump.nopOut();
                } else {
                    // It might still be legit - if we've ended a loop, it's not.
                    Set<BlockIdentifier> changes = SetUtil.difference(maybeJump.getBlockIdentifiers(),statements.get(x+1).getBlockIdentifiers());
                    boolean ok = true;
                    for (BlockIdentifier change : changes) {
                        if (change.getBlockType().isLoop()) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        maybeJump.nopOut();
                    }
                }
            }
        }

        for (Op03SimpleStatement statement : statements) {
            Statement innerStatement = statement.getStatement();
            if (innerStatement instanceof JumpingStatement &&
                    statement.getSources().size() == 1 &&
                    statement.getTargets().size() == 1) {
                Op03SimpleStatement prior = statement.getSources().get(0);
                Statement innerPrior = prior.getStatement();
                if (innerPrior instanceof JumpingStatement) {
                    JumpingStatement jumpInnerPrior = (JumpingStatement) innerPrior;
                    Statement jumpingInnerPriorTarget = jumpInnerPrior.getJumpTarget();
                    if (jumpingInnerPriorTarget == innerStatement &&
                            movableJump(jumpInnerPrior.getJumpType())) {
                        statement.nopOut();
                    }
                }
            }
        }

        /*
         * Do this backwards.  Generally, there'll be more chains shortened that way.
         */
        for (int x = statements.size() - 1; x >= 0; --x) {
            Op03SimpleStatement statement = statements.get(x);
            Statement innerStatement = statement.getStatement();
            if (innerStatement.getClass() == GotoStatement.class) {
                GotoStatement innerGoto = (GotoStatement) innerStatement;
                if (innerGoto.getJumpType() == JumpType.BREAK) continue;
                Op03SimpleStatement target = statement.getTargets().get(0);
                Op03SimpleStatement ultimateTarget = Misc.followNopGotoChain(target, false, false);
                if (target != ultimateTarget) {
                    ultimateTarget = maybeMoveTarget(ultimateTarget, statement, statements);
                    target.removeSource(statement);
                    statement.replaceTarget(target, ultimateTarget);
                    ultimateTarget.addSource(statement);
                }
            } else if (innerStatement.getClass() == IfStatement.class) {
                IfStatement ifStatement = (IfStatement) innerStatement;
                if (!movableJump(ifStatement.getJumpType())) continue;
                Op03SimpleStatement target = statement.getTargets().get(1);
                Op03SimpleStatement ultimateTarget = Misc.followNopGotoChain(target, false, false);
                if (target != ultimateTarget) {
                    ultimateTarget = maybeMoveTarget(ultimateTarget, statement, statements);
                    target.removeSource(statement);
                    statement.replaceTarget(target, ultimateTarget);
                    ultimateTarget.addSource(statement);
                }

            }

        }
    }

    /*
     * If we're jumping into an instruction just after a try block, (or multiple try blocks), we move the jump to the try
     * block, IF we're jumping from outside the try block.
     */
    private static Op03SimpleStatement maybeMoveTarget(Op03SimpleStatement expectedRetarget, Op03SimpleStatement source, List<Op03SimpleStatement> statements) {
        if (expectedRetarget.getBlockIdentifiers().equals(source.getBlockIdentifiers())) return expectedRetarget;

        final int startIdx = statements.indexOf(expectedRetarget);
        int idx = startIdx;
        Op03SimpleStatement maybe = null;
        while (idx > 0 && statements.get(--idx).getStatement() instanceof TryStatement) {
            maybe = statements.get(idx);
            if (maybe.getBlockIdentifiers().equals(source.getBlockIdentifiers())) break;
        }
        if (maybe == null) return expectedRetarget;
        return maybe;
    }


    private static boolean movableJump(JumpType jumpType) {
        switch (jumpType) {
            case BREAK:
            case GOTO_OUT_OF_IF:
            case CONTINUE:
                return false;
            default:
                return true;
        }
    }

}
