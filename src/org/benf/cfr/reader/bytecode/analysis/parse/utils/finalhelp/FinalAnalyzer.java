package org.benf.cfr.reader.bytecode.analysis.parse.utils.finalhelp;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/08/2013
 * Time: 06:20
 */
public class FinalAnalyzer {
    /*
 * This is the INITIAL entry point - i.e. we'll call this once for a finally block.
 * If it's recalled later for a different try in the same block, that try should have already been nopped out.
 */
    public static boolean identifyFinally(Method method, Op03SimpleStatement in, List<Op03SimpleStatement> allStatements,
                                          BlockIdentifierFactory blockIdentifierFactory,
                                          Set<Op03SimpleStatement> analysedTries) {
        // Already modified.
        if (!(in.getStatement() instanceof TryStatement)) return true;
        analysedTries.add(in);

        TryStatement tryStatement = (TryStatement) in.getStatement();
        final BlockIdentifier tryBlockIdentifier = tryStatement.getBlockIdentifier();


        /*
         * We only need worry about try statements which have a 'Throwable' handler.
         */
        List<Op03SimpleStatement> targets = in.getTargets();
        List<Op03SimpleStatement> catchStarts = Functional.filter(targets, new Op03SimpleStatement.TypeFilter<CatchStatement>(CatchStatement.class));
        Set<Op03SimpleStatement> possibleCatches = SetFactory.newOrderedSet();
        for (Op03SimpleStatement catchS : catchStarts) {
            CatchStatement catchStatement = (CatchStatement) catchS.getStatement();
            List<ExceptionGroup.Entry> exceptions = catchStatement.getExceptions();
            for (ExceptionGroup.Entry exception : exceptions) {
                if (exception.getExceptionGroup().getTryBlockIdentifier() == tryBlockIdentifier) {
                    JavaRefTypeInstance catchType = exception.getCatchType();
                    if (TypeConstants.throwableName.equals(catchType.getRawName())) {
                        possibleCatches.add(catchS);
                    }
                }
            }
        }
        if (possibleCatches.isEmpty()) {
            return false;
        }

        /*
         * Find all the LEGITIMATE paths out of this finally block.
         * If there's a direct return and we haven't left the finally block, then we don't need to
         * worry about a finally etc.
         *
         * A JSR counts :(
         */
        final Set<Op03SimpleStatement> exitPaths = SetFactory.newOrderedSet();
        GraphVisitor<Op03SimpleStatement> gv = new GraphVisitorDFS<Op03SimpleStatement>(in.getTargets().get(0), new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
            @Override
            public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                if (arg1.getBlockIdentifiers().contains(tryBlockIdentifier)) {
                    arg2.enqueue(arg1.getTargets());
                } else {
                    exitPaths.add(arg1);
                }
            }
        });
        gv.process();

        /*
         * We have to cheat, and assume the 'furthest' throwable on the chain from our exception handlers
         * is the throwable.
         */
        Op03SimpleStatement possibleFinallyCatch = findPossibleFinallyCatch(possibleCatches, allStatements);

        /* We've got a possible body for the finally from the catch statement, AND maybe
         * an 'end of try' finally body.
         */
        FinallyCatchBody finallyCatchBody = FinallyCatchBody.build(possibleFinallyCatch, allStatements);
        if (finallyCatchBody == null) {
            return false;
        }

        FinallyGraphHelper finallyGraphHelper = new FinallyGraphHelper(finallyCatchBody);

        PeerTries peerTries = new PeerTries(finallyGraphHelper, possibleFinallyCatch);

        /*
         * This IDENTIFIES blocks of code which are the finally attached to this try statement, and
         * ALSO adds try statements which this jumps into, to peerTries.
         *
         * We then loop so that all peerTries can be connected.
         */

        peerTries.add(in);
        Set<Result> results = SetFactory.newOrderedSet();
        while (peerTries.hasNext()) {
            Op03SimpleStatement tryS = peerTries.removeNext();
            if (!identifyFinally2(tryS, allStatements, peerTries, finallyGraphHelper, results)) {
                return false;
            }
        }

        if (results.isEmpty()) {
            // No finally detected.
            return false;
        }

        if (results.size() == 1) {
            // Not worth it!
            return false;
        }

        /*
         * Looking at the ORIGINAL try block, find the last catch block for it.
         */
        List<Op03SimpleStatement> originalTryTargets = ListFactory.newList(SetFactory.newOrderedSet(in.getTargets()));
        Collections.sort(originalTryTargets, new Op03SimpleStatement.CompareByIndex());
        Op03SimpleStatement lastCatch = originalTryTargets.get(originalTryTargets.size() - 1);
        if (!(lastCatch.getStatement() instanceof CatchStatement)) {
            // For a try / finally, we'll still have a pointless catch-rethrow.
            return false;
//            throw new IllegalStateException("Last target of a try not a catch");
        }

        /*
         * We will unlink the finally catch block from all try statements, then
         * try to run peerSets at the same level together.
         */
        List<PeerTries.PeerTrySet> triesByLevel = peerTries.getPeerTryGroups();

        Set<Op03SimpleStatement> catchBlocksToNop = SetFactory.newOrderedSet();
        /*
         * We need to pick an exemplar for the finally body, and insert it after the final, outermost catch.
         */
        final PeerTries.PeerTrySet originalTryGroupPeers = triesByLevel.get(0);
        for (final PeerTries.PeerTrySet peerSet : triesByLevel) {
            boolean firstTryInBlock = true;
            boolean artificalTry = true;

            for (Op03SimpleStatement peerTry : peerSet.getPeerTries()) {
                if (peerTry == in) {
                    peerTry.removeTarget(possibleFinallyCatch);
                    possibleFinallyCatch.removeSource(peerTry);

                    /*
                     * We need to unlink the original from the expected finally catch, but that is it.
                     */
                    firstTryInBlock = false;
                    continue;
                }

                TryStatement peerTryStmt = (TryStatement) peerTry.getStatement();
                final BlockIdentifier oldBlockIdent = peerTryStmt.getBlockIdentifier();
                /*
                 * Decide whether this try really is artificial.
                 * If it's got a target which is other than the outer throwable, then
                 */
                List<Op03SimpleStatement> handlers = ListFactory.newList(peerTry.getTargets());
//                if (firstTryInBlock) {
//                    firstTryInBlock = false;
//                    Set<Op03SimpleStatement> inHandlers = SetFactory.newSet(originalTryTargets);
//                    for (int x=1, len=handlers.size();x<len;++x) {
//                        if (!inHandlers.contains(handlers.get(x))) {
//                            artificalTry = false;
//                        }
//                    }
//                    if (artificalTry) {
//                        continue;
//                    }
//                }

                /*
                 * Unlink this peer try from its catch handlers, (move them into the original try).
                 */
                for (int x = 1, len = handlers.size(); x < len; ++x) {
                    Op03SimpleStatement tgt = handlers.get(x);
                    tgt.removeSource(peerTry);
                    peerTry.removeTarget(tgt);
                    CatchStatement catchStatement = (CatchStatement) tgt.getStatement();
                    final BlockIdentifier catchBlockIdent = catchStatement.getCatchBlockIdent();
                    catchStatement.removeCatchBlockFor(oldBlockIdent);
                    /*
                     * We need to remove tgt (and its entire catchblock) from the block set of
                     * any removed inner.
                     */
                    List<Op03SimpleStatement> catchSources = tgt.getSources();
                    final Set<BlockIdentifier> unionBlocks = SetFactory.newSet();
                    for (Op03SimpleStatement catchSource : catchSources) {
                        unionBlocks.addAll(catchSource.getBlockIdentifiers());
                    }
                    /*
                     * Now, find the blocks that tgt THINKS it's in which are not in this, and remove them from tgt,
                     * AND from every statement that belongs to tgt.
                     */
                    final Set<BlockIdentifier> previousTgtBlocks = SetFactory.newSet(tgt.getBlockIdentifiers());
                    previousTgtBlocks.removeAll(unionBlocks);
                    /*
                     * The remainder are the blocks we SHOULD NO LONGER be in.
                     */
                    tgt.getBlockIdentifiers().removeAll(previousTgtBlocks);
                    if (!previousTgtBlocks.isEmpty()) {
                        tgt.getBlockIdentifiers().removeAll(previousTgtBlocks);
                        GraphVisitor<Op03SimpleStatement> gv2 = new GraphVisitorDFS<Op03SimpleStatement>(tgt.getTargets(), new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                            @Override
                            public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                                if (arg1.getBlockIdentifiers().contains(catchBlockIdent)) {
                                    arg1.getBlockIdentifiers().removeAll(previousTgtBlocks);
                                    arg2.enqueue(arg1.getTargets());
                                }
                            }
                        });
                        gv2.process();
                    }

                    if (tgt.getSources().isEmpty()) {
                        /*
                         * Nop out entire catch block.
                         */
                        catchBlocksToNop.add(tgt);
                    }
                }

                peerTry.nopOut();
                if (peerSet.equals(originalTryGroupPeers)) {
                    //throw new IllegalStateException();
                    peerTry.getBlockIdentifiers().add(tryBlockIdentifier);
                }
                GraphVisitor<Op03SimpleStatement> gvpeer = new GraphVisitorDFS<Op03SimpleStatement>(handlers.get(0), new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                    @Override
                    public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                        Set<BlockIdentifier> blockIdentifiers = arg1.getBlockIdentifiers();
                        if (blockIdentifiers.remove(oldBlockIdent)) {
                            if (peerSet == originalTryGroupPeers) {
                                blockIdentifiers.add(tryBlockIdentifier);
                            }
                            arg2.enqueue(arg1.getTargets());
                        }
                    }
                });
                gvpeer.process();

            }
        }


        CatchStatement catchStatement = (CatchStatement) lastCatch.getStatement();
        BlockIdentifier lastCatchIdent = catchStatement.getCatchBlockIdent();
        int found = -1;
        for (int x = allStatements.size() - 1; x >= 0; --x) {
            if (allStatements.get(x).getBlockIdentifiers().contains(lastCatchIdent)) {
                found = x;
                break;
            }
        }
        if (found == -1) {
            throw new IllegalStateException("Last catch has completely empty body");
        }
        Op03SimpleStatement lastCatchContentStatement = allStatements.get(found);
        InstrIndex newIdx = lastCatchContentStatement.getIndex().justAfter();
//        Op03SimpleStatement afterLast = found+1 >= allStatements.size() ? null : allStatements.get(found+1);

        Result cloneThis = results.iterator().next();
        List<Op03SimpleStatement> oldFinallyBody = ListFactory.newList(cloneThis.getToRemove());
        Collections.sort(oldFinallyBody, new Op03SimpleStatement.CompareByIndex());
        List<Op03SimpleStatement> newFinallyBody = ListFactory.newList();
        Set<BlockIdentifier> oldStartBlocks = SetFactory.newOrderedSet(oldFinallyBody.get(0).getBlockIdentifiers());

        /*
         * AND ADD ALL THE BLOCKS IT SHOULD BE IN!
         */
        Set<BlockIdentifier> extraBlocks = SetFactory.newOrderedSet(in.getBlockIdentifiers());


        // TODO : BlockType.finally?
        BlockIdentifier finallyBlock = blockIdentifierFactory.getNextBlockIdentifier(BlockType.CATCHBLOCK);
        FinallyStatement finallyStatement = new FinallyStatement(finallyBlock);
        Op03SimpleStatement finallyOp = new Op03SimpleStatement(extraBlocks, finallyStatement, newIdx);
        newIdx = newIdx.justAfter();
        newFinallyBody.add(finallyOp);

        extraBlocks.add(finallyBlock);
        Map<Op03SimpleStatement, Op03SimpleStatement> old2new = MapFactory.newMap();
        for (Op03SimpleStatement old : oldFinallyBody) {
            Statement statement = old.getStatement();
            Set<BlockIdentifier> newblocks = SetFactory.newOrderedSet(old.getBlockIdentifiers());
            newblocks.removeAll(oldStartBlocks);
            newblocks.addAll(extraBlocks);
            Op03SimpleStatement newOp = new Op03SimpleStatement(newblocks, statement, old.getSSAIdentifiers(), newIdx);
            newFinallyBody.add(newOp);
            newIdx = newIdx.justAfter();
            old2new.put(old, newOp);
        }
        if (newFinallyBody.size() > 1) {
            newFinallyBody.get(1).markFirstStatementInBlock(finallyBlock);
        }


        /*
         * And add a nop after the end to redirect jumps to.
         */

        /*
         * If afterEnd is a backjump from cloneThis, we've lifted a finally out of a loop (probably!!)
         *
         */
        Op03SimpleStatement endRewrite = null;
        for (Result r : results) {
            Op03SimpleStatement rAfterEnd = r.getAfterEnd();
            if (rAfterEnd != null && rAfterEnd.getIndex().isBackJumpFrom(r.getStart())) {
                endRewrite = new Op03SimpleStatement(extraBlocks, new GotoStatement(), newIdx);
                endRewrite.addTarget(rAfterEnd);
                rAfterEnd.addSource(endRewrite);
                break;
            }
        }
        if (endRewrite == null) {
            endRewrite = new Op03SimpleStatement(extraBlocks, new CommentStatement(""), newIdx);
        }
        //endRewrite.getBlockIdentifiers().remove(finallyBlock);
        newFinallyBody.add(endRewrite);

        for (Op03SimpleStatement old : oldFinallyBody) {
            Op03SimpleStatement newOp = old2new.get(old);
            for (Op03SimpleStatement src : old.getSources()) {
                Op03SimpleStatement newSrc = old2new.get(src);
                if (newSrc == null) {
                    continue;
//                    newSrc = src;
//                    src.addTarget(newOp);
//                    throw new IllegalStateException();
                }
                newOp.addSource(newSrc);
            }
            for (Op03SimpleStatement tgt : old.getTargets()) {
                Op03SimpleStatement newTgt = old2new.get(tgt);
                if (newTgt == null) {
                    if (Op03SimpleStatement.followNopGotoChain(tgt, false) == cloneThis.getAfterEnd()) {
                        /*
                         * It's not in the block....
                         *
                         */

                        endRewrite.addSource(newOp);
                        newTgt = endRewrite;
                        // allow.
//                        newTgt = tgt;
//                        while (newTgt.getStatement().getClass() == GotoStatement.class) {
//                            newTgt = newTgt.getTargets().get(0);
//                        }
//                        newTgt.addSource(newOp);
                    } else {
                        if (!(newOp.getStatement() instanceof JumpingStatement)) {
                            continue;
                        }
                        if (tgt.getIndex().isBackJumpFrom(endRewrite)) {
                            newTgt = tgt;
                            tgt.addSource(newOp);
                        } else {
                            endRewrite.addSource(newOp);
                            newOp.addTarget(endRewrite);


                            if (!endRewrite.getTargets().contains(tgt)) {
                                endRewrite.addTarget(tgt);
                                tgt.addSource(endRewrite);
                            }
                            continue;
                        }
                    }
                }
                newOp.addTarget(newTgt);
            }
        }
        if (newFinallyBody.size() >= 2) {
            Op03SimpleStatement startFinallyCopy = newFinallyBody.get(1);
            startFinallyCopy.addSource(finallyOp);
            finallyOp.addTarget(startFinallyCopy);
        }


        /*
         * Now, nop out all the content.  If proxyThrow exists, add that to the tryblock,
         * and point anything that was pointing at [0] to proxyThrow.
         */
        for (Result result : results) {
            Op03SimpleStatement start = result.getStart();
            Set<Op03SimpleStatement> toRemove = result.getToRemove();
            Op03SimpleStatement afterEnd = result.getAfterEnd();

            List<Op03SimpleStatement> startSources = ListFactory.newList(start.getSources());
            for (Op03SimpleStatement source : startSources) {
                if (!toRemove.contains(source)) {
                    if (afterEnd != null) {
                        boolean canDirect = source.getStatement() instanceof JumpingStatement || source.getIndex().isBackJumpFrom(afterEnd);
                        if (canDirect) {
                            if (source.getStatement().getClass() == IfStatement.class) {
                                if (start == source.getTargets().get(0)) canDirect = false;
                            }
                        }
                        if (canDirect) {
                            source.replaceTarget(start, afterEnd);
                            afterEnd.addSource(source);
                        } else {
                            Op03SimpleStatement afterSource = new Op03SimpleStatement(source.getBlockIdentifiers(), new GotoStatement(), source.getIndex().justAfter());
                            afterEnd.addSource(afterSource);
                            afterSource.addTarget(afterEnd);
                            afterSource.addSource(source);
                            source.replaceTarget(start, afterSource);
                            allStatements.add(afterSource);
                        }
                    } else {
                        Statement sourceStatement = source.getStatement();
                        if (sourceStatement.getClass() == GotoStatement.class) {
                            source.replaceStatement(new Nop());
                            source.removeTarget(start);
                        } else if (sourceStatement.getClass() == IfStatement.class) {
                            /* If which peters out into finally body.
                             * We need our if to jump /somewhere/, so swap the targets around,
                             * reverse the condition, and insert a nop just before the old fall through
                             */
                            IfStatement ifStatement = (IfStatement) sourceStatement;
                            boolean flip = (ifStatement.getJumpTarget().getContainer() == start);
                            if (!flip) throw new IllegalStateException("If jumping OVER finally body.");

                            source.replaceTarget(start, endRewrite);
                            endRewrite.addSource(source);
                        } else {
                            /*
                             *
                             */
                            JavaTypeInstance returnType = method.getMethodPrototype().getReturnType();
                            if (returnType == RawJavaType.VOID) {
                                source.removeTarget(start);
                                // Append return.
                            } else if (sourceStatement instanceof AssignmentSimple) {
                                AssignmentSimple sourceAssignment = (AssignmentSimple) sourceStatement;
                                LValue lValue = sourceAssignment.getCreatedLValue();
                                JavaTypeInstance lValueType = lValue.getInferredJavaType().getJavaTypeInstance();
                                if (lValueType.implicitlyCastsTo(lValueType)) {
                                    Op03SimpleStatement afterSource = new Op03SimpleStatement(source.getBlockIdentifiers(), new ReturnValueStatement(new LValueExpression(lValue), returnType), source.getIndex().justAfter());
                                    source.replaceTarget(start, afterSource);
                                    afterSource.addSource(source);
                                    allStatements.add(afterSource);
                                } else {
                                    source.removeTarget(start);
                                }
                            } else {
                                source.removeTarget(start);
                            }
                        }
                    }
                }

            }
            for (Op03SimpleStatement remove : toRemove) {
                for (Op03SimpleStatement source : remove.getSources()) {
                    source.getTargets().remove(remove);
                }
                for (Op03SimpleStatement target : remove.getTargets()) {
                    target.getSources().remove(remove);
                }
                remove.getSources().clear();
                remove.getTargets().clear();
                remove.nopOut();
            }
            if (afterEnd != null) {
                List<Op03SimpleStatement> endSources = ListFactory.newList(afterEnd.getSources());
                for (Op03SimpleStatement source : endSources) {
                    if (toRemove.contains(source)) {
                        afterEnd.removeSource(source);
                    }
                }
            }
        }

        /*
         * Now go through the ORIGINAL try peers groups' statements again - if any of the
         * non-block reachables is a jump / return / throw, and has only sources in the try block,
         * AND is linear (other than nopped out instructions) then we add it to the try block.
         *
         * Only one of the top peer tries should be a try statement now, but we'll walk the whole set
         * incase something went wrong earlier.
         */
        for (Op03SimpleStatement topTry : originalTryGroupPeers.getPeerTries()) {
            /*
             * This feels like something we should have been able to cache......
             * FIXME.
             */
            Statement topStatement = topTry.getStatement();
            if (!(topStatement instanceof TryStatement)) continue;

            TryStatement topTryStatement = (TryStatement) topStatement;
            final BlockIdentifier topTryIdent = topTryStatement.getBlockIdentifier();

            final Set<Op03SimpleStatement> peerTryExits = SetFactory.newOrderedSet();

            GraphVisitor<Op03SimpleStatement> gv2 = new GraphVisitorDFS<Op03SimpleStatement>(topTry.getTargets().get(0), new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                @Override
                public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                    if (arg1.getBlockIdentifiers().contains(topTryIdent)) {
                        arg2.enqueue(arg1.getTargets());
                    } else {
                        peerTryExits.add(arg1);
                    }
                }
            });
            gv2.process();

            /*
             * For each peerTryExit, if it's only reachable from blocks inside topTryIdent, add it to that block.
             */
            peerloop:
            for (Op03SimpleStatement peerTryExit : peerTryExits) {
                for (Op03SimpleStatement source : peerTryExit.getSources()) {
                    if (!source.getBlockIdentifiers().contains(topTryIdent)) continue peerloop;
                }
                // Because I have a nasty finally hack whereby the finally end isn't in the sources here,
                // we have to make sure that the finally block is AFTER this statement we're going to claim.
                if (peerTryExit.getIndex().isBackJumpFrom(finallyOp)) {
                    peerTryExit.getBlockIdentifiers().add(topTryIdent);
                }
            }
        }


        /*
         * Remove any dead catch blocks.
         */



        /*
         * And, finally, link the try op to the finally.
         */
        in.addTarget(finallyOp);
        finallyOp.addSource(in);
        allStatements.addAll(newFinallyBody);

        return true;
    }


    public static boolean identifyFinally2(final Op03SimpleStatement in, List<Op03SimpleStatement> allStatements,
                                           PeerTries peerTries,
                                           FinallyGraphHelper finallyGraphHelper,
                                           Set<Result> results) {
        if (!(in.getStatement() instanceof TryStatement)) return false;
        TryStatement tryStatement = (TryStatement) in.getStatement();
        final BlockIdentifier tryBlockIdentifier = tryStatement.getBlockIdentifier();


        /*
         * We only need worry about try statements which have a 'Throwable' handler.
         */
        List<Op03SimpleStatement> targets = in.getTargets();
        List<Op03SimpleStatement> catchStarts = Functional.filter(targets, new Op03SimpleStatement.TypeFilter<CatchStatement>(CatchStatement.class));
        Set<Op03SimpleStatement> possibleCatches = SetFactory.newOrderedSet();
        for (Op03SimpleStatement catchS : catchStarts) {
            CatchStatement catchStatement = (CatchStatement) catchS.getStatement();
            List<ExceptionGroup.Entry> exceptions = catchStatement.getExceptions();
            for (ExceptionGroup.Entry exception : exceptions) {
                if (exception.getExceptionGroup().getTryBlockIdentifier() == tryBlockIdentifier) {
                    JavaRefTypeInstance catchType = exception.getCatchType();
                    if (TypeConstants.throwableName.equals(catchType.getRawName())) {
                        possibleCatches.add(catchS);
                    }
                }
            }
        }
        if (possibleCatches.isEmpty()) {
            return false;
        }

        /*
         * Find all the LEGITIMATE paths out of this finally block.
         * If there's a direct return and we haven't left the finally block, then we don't need to
         * worry about a finally etc.
         *
         * A JSR counts :(
         */
        final Set<Op03SimpleStatement> exitPaths = SetFactory.newOrderedSet();
        GraphVisitor<Op03SimpleStatement> gv = new GraphVisitorDFS<Op03SimpleStatement>(in.getTargets().get(0), new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
            @Override
            public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                if (arg1.getBlockIdentifiers().contains(tryBlockIdentifier)) {
                    arg2.enqueue(arg1.getTargets());
                } else {
                    exitPaths.add(arg1);
                }
            }
        });
        gv.process();

        /*
         * See if this block jumps into any peerTries, in which case we add them to peerTries.
         */
        filterPeerTries(exitPaths, peerTries); // , possibleCatches);

        /*
         * Regardless of mechanism, we have a possible body for the finally.
         * Now, we need to recursively validate every catch body that this try block has, and make sure that
         * every way out of it ends up in an identical block.
         */
        for (Op03SimpleStatement legitExitStart : exitPaths) {
            Result legitExitResult = finallyGraphHelper.match(legitExitStart);
            if (legitExitResult.isFail()) {
                return false;
            }
            results.add(legitExitResult);
        }

        List<Op03SimpleStatement> tryTargets = in.getTargets();
        Set<Op03SimpleStatement> seen = SetFactory.newOrderedSet();
        for (int x = 1, len = tryTargets.size(); x < len; ++x) {
            Op03SimpleStatement tryCatch = tryTargets.get(x);
            if (!verifyCatchFinally(tryCatch, finallyGraphHelper, peerTries, results)) {
                /*
                 * It's not this finally. :P
                 */
                return false;
            }
        }

        /*
         * Ok.  We've verified everything, and possibly figured out a 'peer'
         */
        return true;
    }

    private static void filterPeerTries(Collection<Op03SimpleStatement> possibleFinally, PeerTries peerTries) { // , Set<Op03SimpleStatement> possibleCatches) {
        Set<Op03SimpleStatement> res = SetFactory.newOrderedSet();
        for (Op03SimpleStatement possible : possibleFinally) {
            if (possible.getStatement() instanceof TryStatement) {
                // Todo : Should this be 'only the finally catch' ?
                if (possible.getTargets().contains(peerTries.getOriginalFinally())) {
//                if (SetUtil.hasIntersection(possibleCatches, possible.getTargets())) {
                    peerTries.add(possible);
                    continue;
                }
            }
            res.add(possible);
        }

        possibleFinally.clear();
        possibleFinally.addAll(res);
    }


    /*
    * The problem with a catch statement is that all of the content is already marked as being in the
    * catch block.
    *
    * This means that we can't do anything clever with 'has it exited the block'.
    *
    * We use a dodgy heuristic - search and find ALL blocks which MIGHT be the finally block.
    *
    * Keep track of them.  Then verify that they are
    *
    * 1) exits for a try block which has a handler which is the guessed finally
    * 2) ... ?
    */
    private static boolean verifyCatchFinally(final Op03SimpleStatement in, final FinallyGraphHelper finallyGraphHelper,
                                              PeerTries peerTries, Set<Result> results) {
        if (!(in.getStatement() instanceof CatchStatement)) {
            return false;
        }
        if (in.getTargets().size() != 1) {
            return false;
        }
        CatchStatement catchStatement = (CatchStatement) in.getStatement();
        final BlockIdentifier catchBlockIdent = catchStatement.getCatchBlockIdent();
        Op03SimpleStatement firstStatementInCatch = in.getTargets().get(0);

        // Not neccessarily in order.
        final List<Op03SimpleStatement> statementsInCatch = ListFactory.newList();

        final Set<Op03SimpleStatement> targetsOutsideCatch = SetFactory.newOrderedSet();
        final Set<Op03SimpleStatement> directExitsFromCatch = SetFactory.newOrderedSet();
        final Map<Op03SimpleStatement, Set<Op03SimpleStatement>> exitParents = MapFactory.newLazyMap(new UnaryFunction<Op03SimpleStatement, Set<Op03SimpleStatement>>() {
            @Override
            public Set<Op03SimpleStatement> invoke(Op03SimpleStatement arg) {
                return SetFactory.newOrderedSet();
            }
        });
        GraphVisitor<Op03SimpleStatement> gv = new GraphVisitorDFS<Op03SimpleStatement>(firstStatementInCatch, new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
            @Override
            public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                if (arg1.getBlockIdentifiers().contains(catchBlockIdent)) {
                    statementsInCatch.add(arg1);
                    arg2.enqueue(arg1.getTargets());
                    for (Op03SimpleStatement tgt : arg1.getTargets()) {
                        exitParents.get(tgt).add(arg1);
                    }
                    Statement statement = arg1.getStatement();
                    if (statement instanceof ReturnStatement) {
                        directExitsFromCatch.add(arg1);
                    }
                } else {
                    // This leaves the catch block....
                    targetsOutsideCatch.add(arg1);
                }
            }
        });
        gv.process();

        /*
         * targetsOutsideCatch are statements which don't return directly themselves, but are targets of a jump
         * FROM the catch statement.  So add exitFromCatch's parents into directExitsFromCatch.
         */
        for (Op03SimpleStatement outsideCatch : targetsOutsideCatch) {
            directExitsFromCatch.addAll(exitParents.get(outsideCatch));
        }

        /*
         * While it might seem mad, rather than search for exit points (etc) and then backtrack
         * to find finally clauses, instead we search for the finally clauses first.
         * This is a little more expensive, but saves backtracking mismatches.
         */
        Op03SimpleStatement finallyCodeStart = finallyGraphHelper.getFinallyCatchBody().getCatchCodeStart();
        /*
         * If finallyCodestart is null, then ... we don't really have a finally statement.
         */
        if (finallyCodeStart == null) {
            return false;
        }

        final Statement finallyStartStatement = finallyCodeStart.getStatement();

        List<Op03SimpleStatement> possibleFinalStarts = Functional.filter(statementsInCatch, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getStatement().getClass() == finallyStartStatement.getClass();
            }
        });

        List<Result> possibleFinallyBlocks = ListFactory.newList();
        for (Op03SimpleStatement possibleFinallyStart : possibleFinalStarts) {
            Result res = finallyGraphHelper.match(possibleFinallyStart);
            if (!res.isFail()) {
                possibleFinallyBlocks.add(res);
            }
        }

        /*
         * Mark which statements are in which identified finally block -
         * that way we can look at the exits from this block and easily see
         * which is in a finally
         */
        Map<Op03SimpleStatement, Result> matchedFinallyBlockMap = MapFactory.newMap();
        for (Result res : possibleFinallyBlocks) {
            for (Op03SimpleStatement b : res.getToRemove()) {
                matchedFinallyBlockMap.put(b, res);
            }
        }


        /*
         * For try blocks which START inside the catch block, and ALSO vector to the same finally
         * as the ORIGINAL outer try, we expect them to have a Result after them.
         */
        List<Op03SimpleStatement> tryStatements = Functional.filter(statementsInCatch, new Op03SimpleStatement.TypeFilter<TryStatement>(TryStatement.class));
        filterPeerTries(tryStatements, peerTries);

        List<Result> matchedFinallyClones = ListFactory.newList();
        /*
         * NOW - Given the exits we THINK we have, find out if any of them are preceeded by a detected
         * finally clone.
         */
        /*
         * If the finally statement 'has a final throw', then we expect exits from this catch which are
         * legit final exits to follow from the finally block.
         *
         * If it DOESN'T have a finally throw, then we expect the exit to BE CONTAINED IN the finally statement.
         */
        if (finallyGraphHelper.getFinallyCatchBody().hasThrowOp()) {
            /*
             * Expect all SOURCES to be in finally blocks.
             */
            for (Op03SimpleStatement exit : directExitsFromCatch) {
                for (Op03SimpleStatement source : exit.getSources()) {
                    Result res = matchedFinallyBlockMap.get(source);
                    if (res == null) {
                        if (exit.getStatement() instanceof ThrowStatement) {
                            // Might be ok.
                            continue;
                        }
                        // Was a return, but didn't go through finally?  Problem.
                        return false;
                    }
                    results.add(res);
                }
            }
        } else {
            /*
             * Expect these to actually be in the finally blocks!
             */
            for (Op03SimpleStatement exit : directExitsFromCatch) {
                Result res = matchedFinallyBlockMap.get(exit);
                if (res == null) {
                    if (exit.getStatement() instanceof ThrowStatement) {
                        // Might be ok.
                        continue;
                    }
                    // Was a return, but didn't go through finally?  Problem.
                    return false;
                }
                results.add(res);
            }

        }
        return true;
    }


    private static Op03SimpleStatement findPossibleFinallyCatch(Set<Op03SimpleStatement> possibleCatches, List<Op03SimpleStatement> allStatements) {
        /*
         * This is a hack.
         */
        List<Op03SimpleStatement> tmp = ListFactory.newList(possibleCatches);
        Collections.sort(tmp, new Op03SimpleStatement.CompareByIndex());
        Op03SimpleStatement catchS = tmp.get(tmp.size() - 1);
        return catchS;
    }


}
