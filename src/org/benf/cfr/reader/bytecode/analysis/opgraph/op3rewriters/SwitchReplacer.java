package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitch;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitchEntry;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;

import java.util.*;

public class SwitchReplacer {
    public static void replaceRawSwitches(Method method, List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory) {
        List<Op03SimpleStatement> switchStatements = Functional.filter(in, new TypeFilter<RawSwitchStatement>(RawSwitchStatement.class));
        // Replace raw switch statements with switches and case statements inline.
        for (Op03SimpleStatement switchStatement : switchStatements) {
            replaceRawSwitch(method, switchStatement, in, blockIdentifierFactory);
        }
        // We've injected 'case' statements, sort to get them into the proper place.
        Collections.sort(in, new CompareByIndex());

//        Dumper d = new Dumper();
//        for (Op03SimpleStatement statement : in) {
//            statement.dumpInner(d);
//        }
//
        // For each of the switch statements, can we find a contiguous range which represents it?
        // (i.e. where the break statement vectors to).
        // for each case statement, we need to find a common successor, however there may NOT be
        // one, i.e. where all branches (or all bar one) cause termination (return/throw)

        // While we haven't yet done any analysis on loop bodies etc, we can make some fairly
        // simple assumptions, (which can be broken by an obfuscator)  - for each case statement
        // (except the last one) get the set of jumps which are to AFTER the start of the next statement.
        // Fall through doesn't count.
        // [These jumps may be legitimate breaks for the switch, or they may be breaks to enclosing statements.]
        // 1 ) If there are no forward jumps, pull the last case out, and make it fall through. (works for default/non default).
        // 2 ) If there are forward jumps, then it's possible that they're ALL to past the end of the switch statement
        //     However, if that's the case, it probable means that we've been obfuscated.  Take the earliest common one.
        //
        // Check each case statement for obfuscation - for all but the last case, all statements in the range [X -> [x+1)
        // without leaving the block.


        switchStatements = Functional.filter(in, new TypeFilter<SwitchStatement>(SwitchStatement.class));
        for (Op03SimpleStatement switchStatement : switchStatements) {
            // removePointlessSwitchDefault(switchStatement);
            examineSwitchContiguity(switchStatement, in);
            moveJumpsToTerminalIfEmpty(switchStatement, in);
        }

    }

    public static void replaceRawSwitch(Method method, Op03SimpleStatement swatch, List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory) {
        List<Op03SimpleStatement> targets = swatch.getTargets();
        RawSwitchStatement switchStatement = (RawSwitchStatement) swatch.getStatement();
        DecodedSwitch switchData = switchStatement.getSwitchData();
        BlockIdentifier switchBlockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.SWITCH);
        /*
         * If all of the targets /INCLUDING DEFAULT/ point at the same place, replace the switch with a goto, and
         * leave it at that.
         */
        Op03SimpleStatement oneTarget = targets.get(0);
        boolean mismatch = false;
        for (int x = 1; x < targets.size(); ++x) {
            Op03SimpleStatement target = targets.get(x);
            if (target != oneTarget) {
                mismatch = true;
                break;
            }
        }
        if (!mismatch) {
            /*
             * TODO : May have to clear out sources / targets?
             */
            swatch.replaceStatement(new GotoStatement());
            return;
        }

        // For each of the switch targets, add a 'case' statement
        // We can add them at the end, as long as we've got a post hoc sort.

        // What happens if there's no default statement?  Not sure java permits?
        List<DecodedSwitchEntry> entries = switchData.getJumpTargets();
        InferredJavaType caseType = switchStatement.getSwitchOn().getInferredJavaType();
        Map<InstrIndex, Op03SimpleStatement> firstPrev = MapFactory.newMap();
        for (int x = 0, len=targets.size(); x < len; ++x) {
            Op03SimpleStatement target = targets.get(x);
            InstrIndex tindex = target.getIndex();
            if (firstPrev.containsKey(tindex)) { // Get previous case statement if already exists, so we stack them.
                target = firstPrev.get(tindex);
            }
            List<Expression> expression = ListFactory.newList();

            // Otherwise we know that our branches are in the same order as our targets.
            List<Integer> vals = entries.get(x).getValue();
            for (Integer val : vals) {
                if (val != null) {
                    expression.add(new Literal(TypedLiteral.getInt(val)));
                }
            }
            Set<BlockIdentifier> blocks = SetFactory.newSet(target.getBlockIdentifiers());
            blocks.add(switchBlockIdentifier);
            BlockIdentifier caseIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.CASE);
            Op03SimpleStatement caseStatement = new Op03SimpleStatement(blocks, new CaseStatement(expression, caseType, switchBlockIdentifier, caseIdentifier), target.getIndex().justBefore());
            // Link casestatement in infront of target - all sources of target should point to casestatement instead, and
            // there should be one link going from caseStatement to target. (it's unambiguous).
            Iterator<Op03SimpleStatement> iterator = target.getSources().iterator();
            while (iterator.hasNext()) {
                Op03SimpleStatement source = iterator.next();
                if (swatch.getIndex().isBackJumpTo(source)) {
                    continue;
                }
                if (source.getIndex().isBackJumpTo(target)) {
                    continue;
                }
                source.replaceTarget(target, caseStatement);
                caseStatement.addSource(source);
                iterator.remove();
            }
            target.addSource(caseStatement);
            caseStatement.addTarget(target);
            in.add(caseStatement);
            firstPrev.put(tindex, caseStatement);
        }

        Cleaner.renumberInPlace(in);
        /*
         * Now, we have one final possible issue - what if a case block is reachable from a LATER block?
         * i.e.
         *
         * case 1:
         *   goto b
         * case 2:
         *   goto a
         * ...
         * ...
         * a:
         *
         * return
         * b:
         * ...
         * ...
         * goto a
         */
        buildSwitchCases(swatch, targets, switchBlockIdentifier, in);

        swatch.replaceStatement(switchStatement.getSwitchStatement(switchBlockIdentifier));
        /*
         * And (as it doesn't matter) reorder swatch's targets, for minimal confusion
         */
        Collections.sort(swatch.getTargets(), new CompareByIndex());
    }

    private static void buildSwitchCases(Op03SimpleStatement swatch, List<Op03SimpleStatement> targets, BlockIdentifier switchBlockIdentifier, List<Op03SimpleStatement> in) {
        Set<BlockIdentifier> caseIdentifiers = SetFactory.newSet();
        /*
         * For each of the case statements - find which is reachable from the others WITHOUT going through
         * the switch again.  Then we might have to move a whole block... (!).
         *
         * If this is the case, we then figure out which order the switch blocks depend on each other, and perform a code
         * reordering walk through the case statements in new order (and re-write the order of the targets.
         */
        Set<Op03SimpleStatement> caseTargets = SetFactory.newSet(targets);
        /*
         * For the START of each block, find if it's reachable from the start of the others, without going through
         * switch.
         * // /Users/lee/Downloads/samples/android/support/v4/app/FragmentActivity.class
         */
        Map<Op03SimpleStatement, InstrIndex> lastStatementBefore = MapFactory.newMap();
        for (Op03SimpleStatement target : targets) {
            CaseStatement caseStatement = (CaseStatement) target.getStatement();
            BlockIdentifier caseBlock = caseStatement.getCaseBlock();

            NodeReachable nodeReachable = new NodeReachable(caseTargets, target, swatch);
            GraphVisitor<Op03SimpleStatement> gv = new GraphVisitorDFS<Op03SimpleStatement>(target, nodeReachable);
            gv.process();

            List<Op03SimpleStatement> backReachable = Functional.filter(nodeReachable.reaches, new Misc.IsForwardJumpTo(target.getIndex()));
            if (backReachable.isEmpty()) continue;

            if (backReachable.size() != 1) {
//                throw new IllegalStateException("Can't handle case statement with multiple reachable back edges to other cases (yet)");
                continue;
            }

            Op03SimpleStatement backTarget = backReachable.get(0);
            /*
             * If this block is contiguous AND fully contains the found nodes, AND has no other sources, we can re-label
             * all of the entries, and place it just before backTarget.
             *
             * Need to cache the last entry, as we might move multiple blocks.
             *
             */
            boolean contiguous = blockIsContiguous(in, target, nodeReachable.inBlock);
            if (target.getSources().size() != 1) {
                /*
                 * Ok, we can't move this above.  But we need to make sure, (if it's the last block) that we've explicitly
                 * marked the contents as being in this switch, to stop movement.
                 */
                if (contiguous) {
                    for (Op03SimpleStatement reachable : nodeReachable.inBlock) {
                        reachable.markBlock(switchBlockIdentifier);
                        // TODO : FIXME : Expensive - can we assume we won't get asked to mark
                        // members of other cases?
                        if (!caseTargets.contains(reachable)) {
                            if (!SetUtil.hasIntersection(reachable.getBlockIdentifiers(), caseIdentifiers)) {
                                reachable.markBlock(caseBlock);
                            }
                        }
                    }
                }
                continue;
            }
            if (!contiguous) {
                // Can't handle this.
                continue;
            }
            // Yay.  Move (in order) all these statements to before backTarget.
            // Well, don't really move them.  Relabel them.
            InstrIndex prev = lastStatementBefore.get(backTarget);
            if (prev == null) {
                prev = backTarget.getIndex().justBefore();
            }
            int idx = in.indexOf(target) + nodeReachable.inBlock.size() - 1;
            for (int i = 0, len = nodeReachable.inBlock.size(); i < len; ++i, --idx) {
                in.get(idx).setIndex(prev);
                prev = prev.justBefore();
            }
            lastStatementBefore.put(backTarget, prev);
//            throw new IllegalStateException("Backjump fallthrough");
        }
    }

    public static void rebuildSwitches(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> switchStatements = Functional.filter(statements, new TypeFilter<SwitchStatement>(SwitchStatement.class));
        /*
         * Get the block identifiers for all switch statements and their cases.
         */
        for (Op03SimpleStatement switchStatement : switchStatements) {
            SwitchStatement switchStatementInr = (SwitchStatement) switchStatement.getStatement();
            Set<BlockIdentifier> allBlocks = SetFactory.newSet();
            allBlocks.add(switchStatementInr.getSwitchBlock());
            for (Op03SimpleStatement target : switchStatement.getTargets()) {
                Statement stmTgt = target.getStatement();
                if (stmTgt instanceof CaseStatement) {
                    allBlocks.add(((CaseStatement) stmTgt).getCaseBlock());
                }
            }
            for (Op03SimpleStatement stm : statements) {
                stm.getBlockIdentifiers().removeAll(allBlocks);
            }
            buildSwitchCases(switchStatement, switchStatement.getTargets(), switchStatementInr.getSwitchBlock(), statements);
        }
        for (Op03SimpleStatement switchStatement : switchStatements) {
            // removePointlessSwitchDefault(switchStatement);
            examineSwitchContiguity(switchStatement, statements);
            moveJumpsToTerminalIfEmpty(switchStatement, statements);
        }

    }

    private static boolean blockIsContiguous(List<Op03SimpleStatement> in, Op03SimpleStatement start, Set<Op03SimpleStatement> blockContent) {
        int idx = in.indexOf(start);
        int len = blockContent.size();
        if (idx + blockContent.size() > in.size()) return false;
        for (int found = 1; found < len; ++found, ++idx) {
            Op03SimpleStatement next = in.get(idx);
            if (!blockContent.contains(next)) {
                return false;
            }
        }
        return true;
    }



    private static boolean examineSwitchContiguity(Op03SimpleStatement switchStatement, List<Op03SimpleStatement> statements) {
        Set<Op03SimpleStatement> forwardTargets = SetFactory.newSet();

        // Create a copy of the targets.  We're going to have to copy because we want to sort.
        List<Op03SimpleStatement> targets = ListFactory.newList(switchStatement.getTargets());
        Collections.sort(targets, new CompareByIndex());

        int idxFirstCase = statements.indexOf(targets.get(0));

        if (idxFirstCase != statements.indexOf(switchStatement) + 1) {
            throw new ConfusedCFRException("First case is not immediately after switch.");
        }

        BlockIdentifier switchBlock = ((SwitchStatement) switchStatement.getStatement()).getSwitchBlock();
        int indexLastInLastBlock = 0;
        // Process all but the last target.  (handle that below, as we may treat it as outside the case block
        // depending on forward targets.
        for (int x = 0; x < targets.size() - 1; ++x) {
            Op03SimpleStatement thisCase = targets.get(x);
            Op03SimpleStatement nextCase = targets.get(x + 1);
            int indexThisCase = statements.indexOf(thisCase);
            int indexNextCase = statements.indexOf(nextCase);
            InstrIndex nextCaseIndex = nextCase.getIndex();

            Statement maybeCaseStatement = thisCase.getStatement();
            if (!(maybeCaseStatement instanceof CaseStatement)) continue;
            CaseStatement caseStatement = (CaseStatement) maybeCaseStatement;
            BlockIdentifier caseBlock = caseStatement.getCaseBlock();

            int indexLastInThis = Misc.getFarthestReachableInRange(statements, indexThisCase, indexNextCase);
            if (indexLastInThis != indexNextCase - 1) {
                // Oh dear.  This is going to need some moving around.
//                throw new ConfusedCFRException("Case statement doesn't cover expected range.");
            }
            indexLastInLastBlock = indexLastInThis;
            for (int y = indexThisCase + 1; y <= indexLastInThis; ++y) {
                Op03SimpleStatement statement = statements.get(y);
                statement.markBlock(caseBlock);
                statement.markBlock(switchBlock);
                if (statement.getJumpType().isUnknown()) {
                    for (Op03SimpleStatement innerTarget : statement.getTargets()) {
                        innerTarget = Misc.followNopGoto(innerTarget, false, false);
                        if (nextCaseIndex.isBackJumpFrom(innerTarget)) {
                            forwardTargets.add(innerTarget);
                        }
                    }
                }
            }
        }
        // Either we have zero forwardTargets, in which case we can take the last statement and pull it out,
        // or we have some forward targets.
        // If so, we assume (!!) that's the end, and verify reachability from the start of the last case.
        Op03SimpleStatement lastCase = targets.get(targets.size() - 1);
        int indexLastCase = statements.indexOf(lastCase);
        int breakTarget = -1;
        BlockIdentifier caseBlock = null;
        int indexLastInThis = 0;
        boolean retieEnd = false;
        if (!forwardTargets.isEmpty()) {
            List<Op03SimpleStatement> lstFwdTargets = ListFactory.newList(forwardTargets);
            Collections.sort(lstFwdTargets, new CompareByIndex());
            Op03SimpleStatement afterCaseGuess = lstFwdTargets.get(0);
            int indexAfterCase = statements.indexOf(afterCaseGuess);

            CaseStatement caseStatement = (CaseStatement) lastCase.getStatement();
            caseBlock = caseStatement.getCaseBlock();

            try {
                indexLastInThis = Misc.getFarthestReachableInRange(statements, indexLastCase, indexAfterCase);
            } catch (CannotPerformDecode e) {
                forwardTargets.clear();
            }
            if (indexLastInThis != indexAfterCase - 1) {
                retieEnd = true;
            }
        }
        if (forwardTargets.isEmpty()) {
            for (int y = idxFirstCase; y <= indexLastInLastBlock; ++y) {
                Op03SimpleStatement statement = statements.get(y);
                statement.markBlock(switchBlock);
            }
            if (indexLastCase != indexLastInLastBlock + 1) {
                throw new ConfusedCFRException("Extractable last case doesn't follow previous");
            }
            lastCase.markBlock(switchBlock);
            breakTarget = indexLastCase + 1;
        } else {
            for (int y = indexLastCase + 1; y <= indexLastInThis; ++y) {
                Op03SimpleStatement statement = statements.get(y);
                statement.markBlock(caseBlock);
            }
            for (int y = idxFirstCase; y <= indexLastInThis; ++y) {
                Op03SimpleStatement statement = statements.get(y);
                statement.markBlock(switchBlock);
            }
            breakTarget = indexLastInThis + 1;
        }
        Op03SimpleStatement breakStatementTarget = statements.get(breakTarget);

        if (retieEnd) {
            Op03SimpleStatement lastInThis = statements.get(indexLastInThis);
            if (lastInThis.getStatement().getClass() == GotoStatement.class) {
                // Add another goto, after lastIn this.  Last in this becomes a break to that.
                Set<BlockIdentifier> blockIdentifiers = SetFactory.newSet(lastInThis.getBlockIdentifiers());
                blockIdentifiers.remove(caseBlock);
                blockIdentifiers.remove(switchBlock);
                Op03SimpleStatement retie = new Op03SimpleStatement(blockIdentifiers, new GotoStatement(), lastInThis.getIndex().justAfter());
                Op03SimpleStatement target = lastInThis.getTargets().get(0);
                Iterator<Op03SimpleStatement> iterator = target.getSources().iterator();
                while (iterator.hasNext()) {
                    Op03SimpleStatement source = iterator.next();
                    if (source.getBlockIdentifiers().contains(switchBlock)) {
                        iterator.remove();
                        retie.addSource(source);
                        source.replaceTarget(target, retie);
                    }
                }
                if (!retie.getSources().isEmpty()) {
                    retie.addTarget(target);
                    target.addSource(retie);
                    statements.add(breakTarget, retie);
                    breakStatementTarget = retie;
                }


            }
        }

        /* Given the assumption that the statement after the switch block is the break target, can we rewrite any
         * of the exits from the switch statement to be breaks?
         */
        for (Op03SimpleStatement breakSource : breakStatementTarget.getSources()) {
            if (breakSource.getBlockIdentifiers().contains(switchBlock)) {
                if (breakSource.getJumpType().isUnknown()) {
                    ((JumpingStatement) breakSource.getStatement()).setJumpType(JumpType.BREAK);
                }
            }
        }

        return true;
    }

    /*
* If we end up in a situation like this
*
* switch (x) {
*   case a:
*   case b:
*   case c:
*   lbl : case d: [or default]
* }
*
* where lbl has no body, then forward jumps to lbl from inside that switch are equivalent to jumping to the
* following statement.
*
* This is related to (but not identical to) removePointlessSwitchDefaults.
*/
    private static void moveJumpsToTerminalIfEmpty(Op03SimpleStatement switchStatement, List<Op03SimpleStatement> statements) {
        SwitchStatement swatch = (SwitchStatement) switchStatement.getStatement();
        Op03SimpleStatement lastTgt = switchStatement.getTargets().get(switchStatement.getTargets().size() - 1);
        BlockIdentifier switchBlock = swatch.getSwitchBlock();
        if (!lastTgt.getBlockIdentifiers().contains(switchBlock)) return; // paranoia.
        if (lastTgt.getTargets().size() != 1) return;
        if (lastTgt.getSources().size() == 1) return;
        Op03SimpleStatement following = lastTgt.getTargets().get(0);
        // Does following directly follow on from lastTgt, and is follwing NOT in the switch?
        if (following.getBlockIdentifiers().contains(switchBlock)) return;

        // Check that lastTgt has multiple FOWARD JUMPING sources.
        List<Op03SimpleStatement> forwardJumpSources = Functional.filter(lastTgt.getSources(), new Misc.IsForwardJumpTo(lastTgt.getIndex()));
        if (forwardJumpSources.size() <= 1) return;

        int idx = statements.indexOf(lastTgt);
        if (idx == 0) return;
        Op03SimpleStatement justBefore = statements.get(idx - 1);
        if (idx >= statements.size() - 1) return;
        if (statements.get(idx + 1) != following) return;

        // For any sources which were pointing to the last target, (other than the switch statement itself),
        // we can re-point them at the instruction after it.
        for (Op03SimpleStatement forwardJumpSource : forwardJumpSources) {
            if (forwardJumpSource == switchStatement ||
                    forwardJumpSource == justBefore) continue;
            forwardJumpSource.replaceTarget(lastTgt, following);
            lastTgt.removeSource(forwardJumpSource);
            following.addSource(forwardJumpSource);

            /*
             * If the forward jumpsource was a goto, we now know it's actually a break!
             */
            Statement forwardJump = forwardJumpSource.getStatement();
            if (forwardJump instanceof JumpingStatement) {
                JumpingStatement jumpingStatement = (JumpingStatement) forwardJump;
                JumpType jumpType = jumpingStatement.getJumpType();
                if (jumpType.isUnknown()) {
                    jumpingStatement.setJumpType(JumpType.BREAK);
                }
            }
        }
    }


    /* This WILL go too far, as we have no way of knowing when the common code ends....
 *
 */
    private static class NodeReachable implements BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>> {

        private final Set<Op03SimpleStatement> otherCases;
        private final Op03SimpleStatement switchStatement;

        private final Op03SimpleStatement start;
        private final List<Op03SimpleStatement> reaches = ListFactory.newList();
        private final Set<Op03SimpleStatement> inBlock = SetFactory.newSet();

        private NodeReachable(Set<Op03SimpleStatement> otherCases, Op03SimpleStatement start, Op03SimpleStatement switchStatement) {
            this.otherCases = otherCases;
            this.switchStatement = switchStatement;
            this.start = start;
        }

        @Override
        public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
            if (arg1 == switchStatement) {
                return;
            }
            if (arg1.getIndex().isBackJumpFrom(start)) {
                // If it's a backjump from the switch statement as well, ignore.  Otherwise we have to process.
                if (arg1.getIndex().isBackJumpFrom(switchStatement)) return;
            }
            if (arg1 != start && otherCases.contains(arg1)) {
                reaches.add(arg1);
                return;
            }
            inBlock.add(arg1);
            arg2.enqueue(arg1.getTargets());
        }
    }

}
