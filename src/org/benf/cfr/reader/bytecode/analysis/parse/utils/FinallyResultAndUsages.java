package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/08/2013
 * Time: 06:09
 */
public class FinallyResultAndUsages {

    private final List<ResultWithCallers> resultWithCallers = ListFactory.newList();

    public void add(FinallyHelper.Result result, Set<Op03SimpleStatement> callers) {
        resultWithCallers.add(new ResultWithCallers(result, callers));
    }

    public boolean isEmpty() {
        return resultWithCallers.isEmpty();
    }

    private static class ResultWithCallers {
        private final FinallyHelper.Result result;
        private final Set<Op03SimpleStatement> callers;

        private ResultWithCallers(FinallyHelper.Result result, Set<Op03SimpleStatement> callers) {
            this.result = result;
            this.callers = callers;
        }
    }

    public void clearCopies(FinallyHelper finallyHelper, List<Op03SimpleStatement> allStatements) {
        Set<Op03SimpleStatement> finalBackjumpTargets = SetFactory.newSet();
        for (ResultWithCallers result : resultWithCallers) {
            Op03SimpleStatement startOfCopy = result.result.getStartOfFinallyCopy();
            // May be null if we have a non standard throw!
            Op03SimpleStatement finalThrowRedirect = result.result.getFinalThrowRedirect();
            TryStatement tryStatement = result.result.getTryStatement();

            /*
             * Determine if the final throw redirect is a backJump from all of the callers,
             * which leaves its try block.
             * If it is, then we've (maybe) taken a finally which was inside a loop
             * and pulled it outside of the loop.
             */

            if (finalThrowRedirect != null) {
                for (Op03SimpleStatement caller : result.callers) {
                    if (finalThrowRedirect.getIndex().isBackJumpFrom(caller) && !(caller.getStatement() instanceof JumpingStatement)) {
                        finalBackjumpTargets.add(finalThrowRedirect);
                    }
                }
            }
        }

        /*
         * If there's exactly one finalBackjumpTarget, then we'll add an unconditional jump to it AFTER
         * the finally block.  This will catch any finallys which are incorrectly moved outside a loop.
         */
        if (finalBackjumpTargets.size() == 1) {
            Op03SimpleStatement finalBackJumpTarget = finalBackjumpTargets.iterator().next();
            Op03SimpleStatement lastInFinally = finallyHelper.getLastInFinally();

            Op03SimpleStatement redirectGoto = new Op03SimpleStatement(lastInFinally.getBlockIdentifiers(), new GotoStatement(), lastInFinally.getIndex().justAfter());
            redirectGoto.addTarget(finalBackJumpTarget);
            finalBackJumpTarget.addSource(redirectGoto);
            allStatements.add(redirectGoto);
        }


        for (ResultWithCallers result : resultWithCallers) {
            Op03SimpleStatement startOfCopy = result.result.getStartOfFinallyCopy();
            // May be null if we have a non standard throw!
            Op03SimpleStatement finalThrowRedirect = result.result.getFinalThrowRedirect();
            TryStatement tryStatement = result.result.getTryStatement();

            /*
             * Determine if the final throw redirect is a backJump from all of the callers,
             * which leaves its try block.
             * If it is, then we've (maybe) taken a finally which was inside a loop
             * and pulled it outside of the loop.
             */

            if (finalThrowRedirect != null) {
                for (Op03SimpleStatement caller : result.callers) {
                    caller.replaceTarget(startOfCopy, finalThrowRedirect);
                    Set<Op03SimpleStatement> proxySources = result.result.getFinalThrowProxySources();
                    for (Op03SimpleStatement proxySource : proxySources) {
                        if (finalThrowRedirect.getSources().contains(proxySource)) {
                            finalThrowRedirect.removeSource(proxySource);
                        }
                    }
                    if (finalThrowRedirect.getIndex().isBackJumpFrom(caller) && !(caller.getStatement() instanceof JumpingStatement)) {
                        Op03SimpleStatement redirectGoto = new Op03SimpleStatement(caller.getBlockIdentifiers(), new GotoStatement(), caller.getIndex().justAfter());
                        redirectGoto.addSource(caller);
                        caller.replaceTarget(finalThrowRedirect, redirectGoto);
                        redirectGoto.addTarget(finalThrowRedirect);
                        finalThrowRedirect.addSource(redirectGoto);
                        allStatements.add(redirectGoto);
                    } else {
                        finalThrowRedirect.addSource(caller);
                    }
                }
            }
            for (Op03SimpleStatement todelete : result.result.getToRemove()) {
                todelete.clear();
            }
            /*
             * Now, while the final throw redirect is a goto or a return, pull it inside
             * the try block.
             */
            if (finalThrowRedirect != null) {
                Op03SimpleStatement current = finalThrowRedirect;
                BlockIdentifier tryBlock = tryStatement.getBlockIdentifier();
                do {
                    Statement currentStatement = current.getStatement();
                    if (currentStatement.getClass() == GotoStatement.class ||
                            currentStatement instanceof ReturnStatement ||
                            currentStatement.getClass() == Nop.class) {
                        current.getBlockIdentifiers().add(tryBlock);
                        if (current.getTargets().size() == 1) {
                            current = current.getTargets().get(0);
                            if (currentStatement.getClass() == Nop.class) continue;
                            break;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                } while (true);
            }
        }
    }
}
