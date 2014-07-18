package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.TernaryExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.stack.StackEntry;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.util.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ConditionalRewriter {


    private static class IsForwardIf implements Predicate<Op03SimpleStatement> {
        @Override
        public boolean test(Op03SimpleStatement in) {
            if (!(in.getStatement() instanceof IfStatement)) return false;
            IfStatement ifStatement = (IfStatement) in.getStatement();
            if (!ifStatement.getJumpType().isUnknown()) return false;
            if (in.getTargets().get(1).getIndex().compareTo(in.getIndex()) <= 0) return false;
            return true;
        }
    }

    public static void identifyNonjumpingConditionals(List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory) {
        boolean success = false;
        Set<Op03SimpleStatement> ignoreTheseJumps = SetFactory.newSet();
        do {
            success = false;
            List<Op03SimpleStatement> forwardIfs = Functional.filter(statements, new IsForwardIf());
            Collections.reverse(forwardIfs);
            for (Op03SimpleStatement forwardIf : forwardIfs) {
                if (considerAsTrivialIf(forwardIf, statements, blockIdentifierFactory, ignoreTheseJumps) ||
                        considerAsSimpleIf(forwardIf, statements, blockIdentifierFactory, ignoreTheseJumps) ||
                        considerAsDexIf(forwardIf, statements, blockIdentifierFactory, ignoreTheseJumps)) {
                    success = true;
                }
            }
        } while (success);
    }

    private static boolean considerAsTrivialIf(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory, Set<Op03SimpleStatement> ignoreTheseJumps) {
        Op03SimpleStatement takenTarget = ifStatement.getTargets().get(1);
        Op03SimpleStatement notTakenTarget = ifStatement.getTargets().get(0);
        int idxTaken = statements.indexOf(takenTarget);
        int idxNotTaken = statements.indexOf(notTakenTarget);
        if (idxTaken != idxNotTaken + 1) return false;
        if (!(takenTarget.getStatement().getClass() == GotoStatement.class &&
                notTakenTarget.getStatement().getClass() == GotoStatement.class &&
                takenTarget.getTargets().get(0) == notTakenTarget.getTargets().get(0))) {
            return false;
        }
        notTakenTarget.replaceStatement(new CommentStatement("empty if block"));
        // Replace the not taken target with an 'empty if statement'.

        return false;
    }

    /*
 * Spot a rather perverse structure that only happens with DEX2Jar, as far as I can tell.
 *
 * if (x) goto b
 * (a:)
 * ....[1] [ LINEAR STATEMENTS (* or already discounted) ]
 * goto c
 * b:
 * ....[2] [ LINEAR STATEMENTS (*) ]
 * goto d
 * c:
 * ....[3]
 *
 * Can be replaced with
 *
 * if (!x) goto a
 * b:
 * ...[2]
 * goto d
 * a:
 * ....[1]
 * c:
 * ....[3]
 *
 * Which, in turn, may allow us to make some more interesting choices later.
 */
    private static boolean considerAsDexIf(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory, Set<Op03SimpleStatement> ignoreTheseJumps) {
        Statement innerStatement = ifStatement.getStatement();
        if (innerStatement.getClass() != IfStatement.class) {
            return false;
        }
        IfStatement innerIfStatement = (IfStatement) innerStatement;

        int startIdx = statements.indexOf(ifStatement);
        int bidx = statements.indexOf(ifStatement.getTargets().get(1));
        if (bidx <= startIdx) return false; // shouldn't happen.
        InstrIndex startIndex = ifStatement.getIndex();
        InstrIndex bIndex = ifStatement.getTargets().get(1).getIndex();
        if (startIndex.compareTo(bIndex) >= 0) {
            return false; // likewise.  Indicates we need a renumber.
        }

        int aidx = startIdx + 1;

        int cidx = findOverIdx(bidx, statements);
        if (cidx == -1) return false;

        int didx = findOverIdx(cidx, statements);
        if (didx == -1) return false;

        if (didx <= cidx) return false;

        Set<Op03SimpleStatement> permittedSources = SetFactory.newSet(ifStatement);
        if (!isRangeOnlyReachable(aidx, bidx, cidx, statements, permittedSources)) return false;
        if (!isRangeOnlyReachable(bidx, cidx, didx, statements, permittedSources)) return false;

        /*
         * Looks like a legitimate reordering - rewrite statements.  Pick up the entire block, and
         * move as per above.
         */
        List<Op03SimpleStatement> alist = statements.subList(aidx, bidx);
        List<Op03SimpleStatement> blist = statements.subList(bidx, cidx);

        /*
         * Nop out the last entry of alist.
         */
        alist.get(alist.size() - 1).nopOut();

        List<Op03SimpleStatement> ifTargets = ifStatement.getTargets();
        // Swap targets.
        Op03SimpleStatement tgtA = ifTargets.get(0);
        Op03SimpleStatement tgtB = ifTargets.get(1);
        ifTargets.set(0, tgtB);
        ifTargets.set(1, tgtA);
        innerIfStatement.setCondition(innerIfStatement.getCondition().getNegated().simplify());

        List<Op03SimpleStatement> acopy = ListFactory.newList(alist);
        blist.addAll(acopy);
        alist = statements.subList(aidx, bidx);
        alist.clear();

        Cleaner.reindexInPlace(statements);

        return true;
    }


    private static int findOverIdx(int startNext, List<Op03SimpleStatement> statements) {
        /*
         * Find a forward goto before b.
         */
        Op03SimpleStatement next = statements.get(startNext);

        Op03SimpleStatement cStatement = null;
        for (int gSearch = startNext - 1; gSearch >= 0; gSearch--) {
            Op03SimpleStatement stm = statements.get(gSearch);
            Statement s = stm.getStatement();
            if (s instanceof Nop) continue;
            if (s.getClass() == GotoStatement.class) {
                Op03SimpleStatement tgtC = stm.getTargets().get(0);
                if (tgtC.getIndex().isBackJumpFrom(next)) return -1;
                cStatement = tgtC;
                break;
            }
            return -1;
        }
        if (cStatement == null) return -1;
        int cidx = statements.indexOf(cStatement);
        return cidx;
    }

    /*
     * Is this structured as
     *
     * ..
     * ..
     * ..
     * goto X
     *
     * Where the range is self-contained.
     */
    private static boolean isRangeOnlyReachable(int startIdx, int endIdx, int tgtIdx, List<Op03SimpleStatement> statements, Set<Op03SimpleStatement> permittedSources) {

        Set<Op03SimpleStatement> reachable = SetFactory.newSet();
        final Op03SimpleStatement startStatement = statements.get(startIdx);
        final Op03SimpleStatement endStatement = statements.get(endIdx);
        final Op03SimpleStatement tgtStatement = statements.get(tgtIdx);
        InstrIndex startIndex = startStatement.getIndex();
        InstrIndex endIndex = endStatement.getIndex();
        InstrIndex finalTgtIndex = tgtStatement.getIndex();

        reachable.add(statements.get(startIdx));
        boolean foundEnd = false;
        for (int idx = startIdx; idx < endIdx; ++idx) {
            Op03SimpleStatement stm = statements.get(idx);
            if (!reachable.contains(stm)) {
                return false;
            }
            // We don't expect this statement to have sources before startIndex or after bIndex.
            // We don't expect any raw gotos ( or targets ) after bIndex / beforeStartIndex. (break / continue are ok).
            for (Op03SimpleStatement source : stm.getSources()) {
                InstrIndex sourceIndex = source.getIndex();
                if (sourceIndex.compareTo(startIndex) < 0) {
                    if (!permittedSources.contains(source)) {
                        return false;
                    }
                }
                if (sourceIndex.compareTo(endIndex) >= 0) {
                    return false;
                }
            }
            for (Op03SimpleStatement target : stm.getTargets()) {
                InstrIndex tgtIndex = target.getIndex();
                if (tgtIndex.compareTo(startIndex) < 0) {
                    return false;
                }
                if (tgtIndex.compareTo(endIndex) >= 0) {
                    if (tgtIndex == finalTgtIndex) {
                        foundEnd = true;
                    } else {
                        return false;
                    }
                }
                reachable.add(target);
            }
        }
        return foundEnd;
    }


    /*
     * Handle this case
     *
     *             ** if (!var7_4.isSuccess() || var7_4.getData() == null || var7_4.getData().size() <= 0) goto lbl-1000
lbl10: // 1 sources:
            var9_5 = var7_4.getData().get(0);
            var8_6 = false;
            if (var9_5 == null) lbl-1000: // 2 sources:
            {
                var8_6 = true;
            }

     * Here we can see that the earlier conditional jumps into the later one after the comparison - the later one
     * can be represented as a break out of an anonymous block covering the early one.  It's a bit messy, but Dex2Jar
     * generates this kind of stuff.
     */
    private static boolean detectAndRemarkJumpIntoOther(Set<BlockIdentifier> blocksAtStart, Set<BlockIdentifier> blocksAtEnd, Op03SimpleStatement realEnd, Op03SimpleStatement ifStatement) {
        if (blocksAtEnd.size() != blocksAtStart.size() + 1) return false;

        List<BlockIdentifier> diff =  SetUtil.differenceAtakeBtoList(blocksAtEnd, blocksAtStart);
        BlockIdentifier testBlock = diff.get(0);
        if (testBlock.getBlockType() != BlockType.SIMPLE_IF_TAKEN) return false;

        // If the difference is a simple-if, AND the statement AFTER realEnd is the target of BOTH
        // realEnd AND the source if statement... AND the if statement is inside our potential range,
        // we can convert THAT to a block exit, and remove that conditional.
        List<Op03SimpleStatement> realEndTargets = realEnd.getTargets();
        if (!(realEndTargets.size() == 1 && realEndTargets.get(0).getLinearlyPrevious() == realEnd)) return false;

        Op03SimpleStatement afterRealEnd = realEndTargets.get(0);
        List<Op03SimpleStatement> areSources = afterRealEnd.getSources();
        if (areSources.size() != 2) return false;
        Op03SimpleStatement other = areSources.get(0) == realEnd ? areSources.get(1) : areSources.get(0);
        // If other is the conditional for testBlock, we can change that jump into a break, as long as
        // the new conditional is a labelled block.
        Statement otherStatement = other.getStatement();
        if (!other.getIndex().isBackJumpTo(ifStatement)) return false;

        if (!(otherStatement instanceof IfStatement)) return false;

        Pair<BlockIdentifier, BlockIdentifier> knownBlocks = ((IfStatement) otherStatement).getBlocks();
        if (!(knownBlocks.getFirst() == testBlock && knownBlocks.getSecond() == null)) return false;

        ((IfStatement) otherStatement).setJumpType(JumpType.BREAK_ANONYMOUS);
        // And we need to unmark anything which is in testBlock.
        Op03SimpleStatement current = other.getLinearlyNext();
        while (current != null && current.getBlockIdentifiers().contains(testBlock)) {
            current.getBlockIdentifiers().remove(testBlock);
            current = current.getLinearlyNext();
        }
        return true;
    }

    /*
    * This is an if statement where both targets are forward.
    *
    * it's a 'simple' if, if:
    *
    * target[0] reaches (incl) the instruction before target[1] without any jumps (other than continue / break).
    *
    * note that the instruction before target[1] doesn't have to have target[1] as a target...
    * (we might have if (foo) return;)
    *
    * If it's a SIMPLE if/else, then the last statement of the if block is a goto, which jumps to after the else
    * block.  We don't want to keep that goto, as we've inferred structure now.
    *
    * We trim that GOTO when we move from an UnstructuredIf to a StructuredIf.
    */
    private static boolean considerAsSimpleIf(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory, Set<Op03SimpleStatement> ignoreTheseJumps) {
        Op03SimpleStatement takenTarget = ifStatement.getTargets().get(1);
        Op03SimpleStatement notTakenTarget = ifStatement.getTargets().get(0);
        int idxTaken = statements.indexOf(takenTarget);
        int idxNotTaken = statements.indexOf(notTakenTarget);
        IfStatement innerIfStatement = (IfStatement) ifStatement.getStatement();

        Set<Op03SimpleStatement> ignoreLocally = SetFactory.newSet();

        boolean takenAction = false;

        int idxCurrent = idxNotTaken;
        if (idxCurrent > idxTaken) return false;

        int idxEnd = idxTaken;
        int maybeElseEndIdx = -1;
        Op03SimpleStatement maybeElseEnd = null;
        boolean maybeSimpleIfElse = false;
        boolean extractCommonEnd = false;
        GotoStatement leaveIfBranchGoto = null;
        Op03SimpleStatement leaveIfBranchHolder = null;
        List<Op03SimpleStatement> ifBranch = ListFactory.newList();
        List<Op03SimpleStatement> elseBranch = null;
        // Consider the try blocks we're in at this point.  (the ifStatemenet).
        // If we leave any of them, we've left the if.
        Set<BlockIdentifier> blocksAtStart = ifStatement.getBlockIdentifiers();
        if (idxCurrent == idxEnd) {
            // It's a trivial tautology? We can't nop it out unless it's side effect free.
            // Instead insert a comment.
            Op03SimpleStatement taken = new Op03SimpleStatement(blocksAtStart, new CommentStatement("empty if block"), notTakenTarget.getIndex().justBefore());
            taken.addSource(ifStatement);
            taken.addTarget(notTakenTarget);
            Op03SimpleStatement emptyTarget = ifStatement.getTargets().get(0);
            if (notTakenTarget != emptyTarget) {
                notTakenTarget.addSource(taken);
            }
            emptyTarget.replaceSource(ifStatement, taken);
            ifStatement.getTargets().set(0, taken);
            statements.add(idxTaken, taken);

            BlockIdentifier ifBlockLabel = blockIdentifierFactory.getNextBlockIdentifier(BlockType.SIMPLE_IF_TAKEN);
            taken.markFirstStatementInBlock(ifBlockLabel);
            // O(N) insertion here is annoying, but we need to fix up the list IMMEDIATELY.
            taken.getBlockIdentifiers().add(ifBlockLabel);
            innerIfStatement.setKnownBlocks(ifBlockLabel, null);
            innerIfStatement.setJumpType(JumpType.GOTO_OUT_OF_IF);
            return true;
        }
        Set<Op03SimpleStatement> validForwardParents = SetFactory.newSet();
        validForwardParents.add(ifStatement);
        /*
         * Find the (possible) end of the if block, which is a forward unconditional jump.
         * If that's the case, cache it and rewrite any jumps we see which go to the same place
         * as double jumps via this unconditional.
         */
        Op03SimpleStatement stmtLastBlock = statements.get(idxTaken - 1);
        Op03SimpleStatement stmtLastBlockRewrite = null;
        Statement stmtLastBlockInner = stmtLastBlock.getStatement();
        if (stmtLastBlockInner.getClass() == GotoStatement.class) {
            stmtLastBlockRewrite = stmtLastBlock;
        }

        do {
            Op03SimpleStatement statementCurrent = statements.get(idxCurrent);
            /* Consider sources of this which jumped forward to get to it.
             *
             */
            InstrIndex currentIndex = statementCurrent.getIndex();
            for (Op03SimpleStatement source : statementCurrent.getSources()) {
                if (currentIndex.isBackJumpTo(source)) {
                    if (!validForwardParents.contains(source)) {
                        // source from outside the block.  This likely means that we've actually left the block.
                        // eg
                        // if (foo) goto Z
                        // ....
                        // return 1;
                        // label:
                        // statement <-- here.
                        // ...
                        // Z
                        //
                        // (although it might mean some horrid duffs device style compiler output).
                        // TODO: CheckForDuff As below.
                        //if (statementIsReachableFrom(statementCurrent, ifStatement)) return false;
                        Op03SimpleStatement newJump = new Op03SimpleStatement(ifStatement.getBlockIdentifiers(), new GotoStatement(), statementCurrent.getIndex().justBefore());
                        if (statementCurrent != ifStatement.getTargets().get(0)) {
                            Op03SimpleStatement oldTarget = ifStatement.getTargets().get(1);
                            newJump.addTarget(oldTarget);
                            newJump.addSource(ifStatement);
                            ifStatement.replaceTarget(oldTarget, newJump);
                            oldTarget.replaceSource(ifStatement, newJump);
                            statements.add(idxCurrent, newJump);
                            return true;
                        }
                    }
                }
            }
            validForwardParents.add(statementCurrent);

            ifBranch.add(statementCurrent);
            JumpType jumpType = statementCurrent.getJumpType();
            if (jumpType.isUnknown() && !ignoreTheseJumps.contains(statementCurrent)) {
                // Todo : Currently we can only cope with hitting
                // the last jump as an unknown jump.  We ought to be able to rewrite
                // i.e. if we have
                /*
                    if (list != null) goto lbl6;
                    System.out.println("A");
                    if (set != null) goto lbl8; <<-----
                    System.out.println("B");
                    goto lbl8;
                    lbl6:
                    ELSE BLOCK
                    lbl8:
                    return true;
                 */
                // this is a problem, because the highlighted statement will cause us to abandon processing.
                if (idxCurrent == idxTaken - 1) {
                    Statement mGotoStatement = statementCurrent.getStatement();
                    if (!(mGotoStatement.getClass() == GotoStatement.class)) return false;
                    GotoStatement gotoStatement = (GotoStatement) mGotoStatement;
                    // It's unconditional, and it's a forward jump.
                    maybeElseEnd = statementCurrent.getTargets().get(0);
                    maybeElseEndIdx = statements.indexOf(maybeElseEnd);
                    if (maybeElseEnd.getIndex().compareTo(takenTarget.getIndex()) <= 0) {
                        return false;
                    }
                    leaveIfBranchHolder = statementCurrent;
                    leaveIfBranchGoto = gotoStatement;
                    maybeSimpleIfElse = true;
                } else {
                    if (stmtLastBlockRewrite == null) {
                        Op03SimpleStatement tgtContainer = statementCurrent.getTargets().get(0);
                        if (tgtContainer == takenTarget) {
                            // We can ignore this statement in future passes.
                            idxCurrent++;
                            continue;
                        }

                        return false;
                    }
                    // We can try to rewrite this block to have an indirect jump via the end of the block,
                    // if that's appropriate.
                    List<Op03SimpleStatement> targets = statementCurrent.getTargets();

                    Op03SimpleStatement eventualTarget = stmtLastBlockRewrite.getTargets().get(0);
                    boolean found = false;
                    for (int x = 0; x < targets.size(); ++x) {
                        Op03SimpleStatement target = targets.get(x);
                        if (target == eventualTarget && target != stmtLastBlockRewrite) {
                            // Equivalent to
                            // statementCurrent.replaceTarget(eventualTarget, stmtLastBlockRewrite);
                            targets.set(x, stmtLastBlockRewrite);
                            stmtLastBlockRewrite.addSource(statementCurrent);
                            // Todo : I don't believe the latter branch will EVER happen.
                            if (eventualTarget.getSources().contains(stmtLastBlockRewrite)) {
                                eventualTarget.removeSource(statementCurrent);
                            } else {
                                eventualTarget.replaceSource(statementCurrent, stmtLastBlockRewrite);
                            }

                            found = true;
                        }
                    }

                    return found;
                }
            }
            idxCurrent++;
        } while (idxCurrent != idxEnd);
        // We've reached the "other" branch of the conditional.
        // If maybeSimpleIfElse is set, then there was a final jump to

        if (maybeSimpleIfElse) {
            // If there is a NO JUMP path between takenTarget and maybeElseEnd, then that's the ELSE block
            elseBranch = ListFactory.newList();
            idxCurrent = idxTaken;
            idxEnd = maybeElseEndIdx;
            do {
                Op03SimpleStatement statementCurrent = statements.get(idxCurrent);
                elseBranch.add(statementCurrent);
                JumpType jumpType = statementCurrent.getJumpType();
                if (jumpType.isUnknown()) {
                    /* We allow ONE unconditional forward jump, to maybeElseEnd.  If we find this, we have
                     * a simple if /else/ block, which we can rewrite as
                     * if (a) { .. .goto X } else { .... goto X } -->
                     * if (a) { ... } else { ....} ; goto X
                     */
                    Statement mGotoStatement = statementCurrent.getStatement();
                    if (!(mGotoStatement.getClass() == GotoStatement.class)) return false;
                    GotoStatement gotoStatement = (GotoStatement) mGotoStatement;
                    // It's unconditional, and it's a forward jump.
                    if (statementCurrent.getTargets().get(0) == maybeElseEnd) {
                        idxEnd = idxCurrent;
                        idxCurrent--;


                        // We can do this aggressively, as it doesn't break the graph.
                        leaveIfBranchHolder.replaceTarget(maybeElseEnd, statementCurrent);
                        statementCurrent.addSource(leaveIfBranchHolder);
                        maybeElseEnd.removeSource(leaveIfBranchHolder);
                        elseBranch.remove(statementCurrent);   // eww.
                        takenAction = true;
                    } else {
                        return false;
                    }
                }
                idxCurrent++;
            } while (idxCurrent != idxEnd);
        }

        Op03SimpleStatement realEnd = statements.get(idxEnd);
        Set<BlockIdentifier> blocksAtEnd = realEnd.getBlockIdentifiers();
        // If we've changed the blocks we're in, that means we've jumped into a block.
        // The only way we can cope with this, is if we're jumping into a block which we subsequently change
        // to be an anonymous escape.
        if (!(blocksAtStart.containsAll(blocksAtEnd) && blocksAtEnd.size() == blocksAtStart.size())) {
            if (!detectAndRemarkJumpIntoOther(blocksAtStart, blocksAtEnd, realEnd, ifStatement)) return takenAction;
        }

        // It's an if statement / simple if/else, for sure.  Can we replace it with a ternary?
        DiscoveredTernary ternary = testForTernary(ifBranch, elseBranch, leaveIfBranchHolder);
        if (ternary != null) {
            // We can ditch this whole thing for a ternary expression.
            for (Op03SimpleStatement statement : ifBranch) statement.nopOut();
            for (Op03SimpleStatement statement : elseBranch) statement.nopOut();
            // todo : do I need to do a more complex merge?
            ifStatement.forceSSAIdentifiers(leaveIfBranchHolder.getSSAIdentifiers());

            // We need to be careful we don't replace x ? 1 : 0 with x here unless we're ABSOLUTELY
            // sure that the final expression type is boolean.....
            // this may come back to bite. (at which point we can inject a (? 1 : 0) ternary...
            ConditionalExpression conditionalExpression = innerIfStatement.getCondition().getNegated().simplify();
            Expression rhs = ternary.isPointlessBoolean() ?
                    conditionalExpression :
                    new TernaryExpression(
                            conditionalExpression,
                            ternary.e1, ternary.e2);

            ifStatement.replaceStatement(
                    new AssignmentSimple(
                            ternary.lValue,
                            rhs
                    )
            );
            // Reduce the ternary lValue's created location count, if we can.
            if (ternary.lValue instanceof StackSSALabel) {
                StackSSALabel stackSSALabel = (StackSSALabel) ternary.lValue;
                StackEntry stackEntry = stackSSALabel.getStackEntry();
                stackEntry.decSourceCount();
//                List<Long> sources = stackEntry.getSources();
//                stackEntry.removeSource(sources.get(sources.size() - 1));
            }

            // If statement now should have only one target.
            List<Op03SimpleStatement> tmp = ListFactory.uniqueList(ifStatement.getTargets());
            ifStatement.getTargets().clear();
            ifStatement.getTargets().addAll(tmp);
            if (ifStatement.getTargets().size() != 1) {
                throw new ConfusedCFRException("If statement should only have one target after dedup");
            }
            Op03SimpleStatement joinStatement = ifStatement.getTargets().get(0);
            tmp = ListFactory.uniqueList(joinStatement.getSources());
            joinStatement.getSources().clear();
            joinStatement.getSources().addAll(tmp);

            /*
             * And now we need to reapply LValue condensing in the region! :(
             */
            LValueProp.condenseLValues(statements);

            return true;
        }


        BlockIdentifier ifBlockLabel = blockIdentifierFactory.getNextBlockIdentifier(BlockType.SIMPLE_IF_TAKEN);
        Misc.markWholeBlock(ifBranch, ifBlockLabel);
        BlockIdentifier elseBlockLabel = null;
        if (maybeSimpleIfElse) {
            elseBlockLabel = blockIdentifierFactory.getNextBlockIdentifier(BlockType.SIMPLE_IF_ELSE);
            if (elseBranch.isEmpty()) {
                elseBlockLabel = null;
                maybeSimpleIfElse = false;
                //throw new IllegalStateException();
            } else {
                Misc.markWholeBlock(elseBranch, elseBlockLabel);
            }
        }

        if (leaveIfBranchGoto != null) leaveIfBranchGoto.setJumpType(JumpType.GOTO_OUT_OF_IF);
        innerIfStatement.setJumpType(JumpType.GOTO_OUT_OF_IF);
        innerIfStatement.setKnownBlocks(ifBlockLabel, elseBlockLabel);
        ignoreTheseJumps.addAll(ignoreLocally);
        return true;
    }


    private static DiscoveredTernary testForTernary(List<Op03SimpleStatement> ifBranch, List<Op03SimpleStatement> elseBranch, Op03SimpleStatement leaveIfBranch) {
        if (ifBranch == null || elseBranch == null) return null;
        if (leaveIfBranch == null) return null;
        TypeFilter<Nop> notNops = new TypeFilter<Nop>(Nop.class, false);
        ifBranch = Functional.filter(ifBranch, notNops);
        switch (ifBranch.size()) {
            case 1:
                break;
            case 2:
                if (ifBranch.get(1) != leaveIfBranch) {
                    return null;
                }
                break;
            default:
                return null;
        }
        elseBranch = Functional.filter(elseBranch, notNops);
        if (elseBranch.size() != 1) {
            return null;
        }

        Op03SimpleStatement s1 = ifBranch.get(0);
        Op03SimpleStatement s2 = elseBranch.get(0);
        if (s2.getSources().size() != 1) return null;
        LValue l1 = s1.getStatement().getCreatedLValue();
        LValue l2 = s2.getStatement().getCreatedLValue();
        if (l1 == null || l2 == null) {
            return null;
        }
        if (!l2.equals(l1)) {
            return null;
        }
        return new DiscoveredTernary(l1, s1.getStatement().getRValue(), s2.getStatement().getRValue());
    }



    private static class DiscoveredTernary {
        LValue lValue;
        Expression e1;
        Expression e2;

        private DiscoveredTernary(LValue lValue, Expression e1, Expression e2) {
            this.lValue = lValue;
            this.e1 = e1;
            this.e2 = e2;
        }

        private static Troolean isOneOrZeroLiteral(Expression e) {
            if (!(e instanceof Literal)) return Troolean.NEITHER;
            TypedLiteral typedLiteral = ((Literal) e).getValue();
            Object value = typedLiteral.getValue();
            if (!(value instanceof Integer)) return Troolean.NEITHER;
            int iValue = (Integer) value;
            if (iValue == 1) return Troolean.TRUE;
            if (iValue == 0) return Troolean.FALSE;
            return Troolean.NEITHER;
        }

        private boolean isPointlessBoolean() {
            if (!(e1.getInferredJavaType().getRawType() == RawJavaType.BOOLEAN &&
                    e2.getInferredJavaType().getRawType() == RawJavaType.BOOLEAN)) return false;

            if (isOneOrZeroLiteral(e1) != Troolean.TRUE) return false;
            if (isOneOrZeroLiteral(e2) != Troolean.FALSE) return false;
            return true;
        }
    }


}
