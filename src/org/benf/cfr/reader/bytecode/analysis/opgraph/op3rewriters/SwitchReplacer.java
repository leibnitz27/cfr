package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CompOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ComparisonOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.TernaryExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableFactory;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitch;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitchEntry;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.collections.*;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;

import java.util.*;

public class SwitchReplacer {
    public static void replaceRawSwitches(Method method, List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory, VariableFactory vf, DecompilerComments decompilerComments, Options options) {
        List<Op03SimpleStatement> switchStatements = Functional.filter(in, new TypeFilter<RawSwitchStatement>(RawSwitchStatement.class));
        // Replace raw switch statements with switches and case statements inline.
        List<Op03SimpleStatement> switches = ListFactory.newList();
        for (Op03SimpleStatement switchStatement : switchStatements) {
            Op03SimpleStatement switchToProcess = replaceRawSwitch(method, switchStatement, in, blockIdentifierFactory, options);
            if (switchToProcess != null) switches.add(switchToProcess);
        }
        // We've injected 'case' statements, sort to get them into the proper place.
        Collections.sort(in, new CompareByIndex());

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


        boolean pullCodeIntoCase = options.getOption(OptionsImpl.PULL_CODE_CASE);
        for (Op03SimpleStatement switchStatement : switches) {
            // (assign switch block).
            examineSwitchContiguity(switchStatement, in, pullCodeIntoCase);
            moveJumpsToCaseStatements(switchStatement);
            moveJumpsToTerminalIfEmpty(switchStatement, in);
            rewriteDuff(switchStatement, in, vf, decompilerComments);
        }
    }

    private static Op03SimpleStatement replaceRawSwitch(@SuppressWarnings("unused") Method method, Op03SimpleStatement swatch, List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory, Options options) {
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
             * The potential issue here is if we've computed an intermediate value to switch on for
             * an enum switch - that will still be hanging around, but with no switch attached to it.
             *
             * We'd rather drop the switch altogether, because a 'fake' switch like this might be used to hide a goto.
             *
             * As a compromise - if the switches single target is directly after the switch, we'll emit
             * it anyway - as an empty switch, but if it's not, we'll drop it completely.
             */
            int idx = in.indexOf(swatch);
            if (idx + 1 >= in.size() || oneTarget != in.get(idx + 1)) {
                // drop it completely.
                swatch.replaceStatement(new GotoStatement(BytecodeLoc.TODO));
                return null;
            }
            // emit an empty switch.
            swatch.replaceStatement(switchStatement.getSwitchStatement(switchBlockIdentifier));
            BlockIdentifier defBlock = blockIdentifierFactory.getNextBlockIdentifier(BlockType.CASE);
            Op03SimpleStatement defStm = new Op03SimpleStatement(swatch.getBlockIdentifiers(),
                    new CaseStatement(BytecodeLoc.TODO, ListFactory.<Expression>newList(), switchStatement.getSwitchOn().getInferredJavaType(), switchBlockIdentifier, defBlock),
                    swatch.getIndex().justAfter()
            );
            swatch.replaceTarget(oneTarget, defStm);
            oneTarget.replaceSource(swatch, defStm);
            defStm.addSource(swatch);
            defStm.addTarget(oneTarget);
            defStm.getBlockIdentifiers().add(switchBlockIdentifier);
            in.add(defStm);
            return null;

        }

        if (options.getOption(OptionsImpl.FORCE_TOPSORT_EXTRA) == Troolean.TRUE) {
            tryInlineRawSwitchContent(swatch, in);
        }

        // For each of the switch targets, add a 'case' statement
        // We can add them at the end, as long as we've got a post hoc sort.
        // What happens if there's no default statement?  Not sure java permits?
        List<DecodedSwitchEntry> entries = switchData.getJumpTargets();
        InferredJavaType caseType = switchStatement.getSwitchOn().getInferredJavaType();
        Map<InstrIndex, Op03SimpleStatement> firstPrev = MapFactory.newMap();
        InstrIndex nextIntermed = swatch.getIndex().justAfter();
        for (int x = 0, len = targets.size(); x < len; ++x) {
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
            Op03SimpleStatement caseStatement = new Op03SimpleStatement(blocks, new CaseStatement(BytecodeLoc.TODO, expression, caseType, switchBlockIdentifier, caseIdentifier), target.getIndex().justBefore());
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
            if (caseStatement.getSources().isEmpty()) {
                // We have to add a case target after the case, and jump from that to the expected target.
                // Order will be FIFO.
                caseStatement.setIndex(nextIntermed);
                nextIntermed = nextIntermed.justAfter();
                Op03SimpleStatement intermediateGoto = new Op03SimpleStatement(blocks, new GotoStatement(BytecodeLoc.TODO), nextIntermed);
                nextIntermed = nextIntermed.justAfter();
                intermediateGoto.addSource(caseStatement);
                intermediateGoto.addTarget(target);
                caseStatement.addTarget(intermediateGoto);
                target.replaceSource(swatch, intermediateGoto);
                swatch.replaceTarget(target, caseStatement);
                caseStatement.addSource(swatch);
                in.add(caseStatement);
                in.add(intermediateGoto);
                continue;
            }
            target.addSource(caseStatement);
            caseStatement.addTarget(target);
            in.add(caseStatement);
            firstPrev.put(tindex, caseStatement);
        }

        Cleaner.sortAndRenumberInPlace(in);
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

        return swatch;
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

            if (!caseStatement.isDefault()) {
                target.markBlock(switchBlockIdentifier);
            }

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

    public static void rebuildSwitches(List<Op03SimpleStatement> statements, Options options) {

        List<Op03SimpleStatement> switchStatements = Functional.filter(statements, new TypeFilter<SwitchStatement>(SwitchStatement.class));
        /*
         * Get the block identifiers for all switch statements and their cases.
         */
        for (Op03SimpleStatement switchStatement : switchStatements) {
            SwitchStatement switchStatementInr = (SwitchStatement) switchStatement.getStatement();
            BlockIdentifier switchBlock = switchStatementInr.getSwitchBlock();

            // un-classify any breaks that we've previously found, otherwise they
            // might not qualify for conversion to labelled jumps.
            // We know that the switch is linear at this point.
            int idx = statements.indexOf(switchStatement.getTargets().get(0));
            for (int len=statements.size();idx<len;++idx) {
                Op03SimpleStatement statement = statements.get(idx);
                if (!statement.getBlockIdentifiers().contains(switchBlock)) {
                    for (Op03SimpleStatement src : statement.getSources()) {
                        if (src.getBlockIdentifiers().contains(switchBlock)) {
                            Statement srcStatement = src.getStatement();
                            if (srcStatement instanceof GotoStatement) {
                                if (((GotoStatement) srcStatement).getJumpType() == JumpType.BREAK) {
                                    ((GotoStatement) srcStatement).setJumpType(JumpType.GOTO);
                                }
                            }
                        }
                    }
                    break;
                }
            }

            Set<BlockIdentifier> allBlocks = SetFactory.newSet();
            allBlocks.add(switchBlock);
            for (Op03SimpleStatement target : switchStatement.getTargets()) {
                Statement stmTgt = target.getStatement();
                if (stmTgt instanceof CaseStatement) {
                    allBlocks.add(((CaseStatement) stmTgt).getCaseBlock());
                }
            }
            for (Op03SimpleStatement stm : statements) {
                stm.getBlockIdentifiers().removeAll(allBlocks);
            }
        }
        for (Op03SimpleStatement switchStatement : switchStatements) {
            SwitchStatement switchStatementInr = (SwitchStatement) switchStatement.getStatement();
            buildSwitchCases(switchStatement, switchStatement.getTargets(), switchStatementInr.getSwitchBlock(), statements);
        }
        boolean pullCodeIntoCase = options.getOption(OptionsImpl.PULL_CODE_CASE);
        for (Op03SimpleStatement switchStatement : switchStatements) {
            // removePointlessSwitchDefault(switchStatement);
            examineSwitchContiguity(switchStatement, statements, pullCodeIntoCase);
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

    private static void examineSwitchContiguity(Op03SimpleStatement switchStatement, List<Op03SimpleStatement> statements,
                                                boolean pullCodeIntoCase) {
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
//            if (indexLastInThis != indexNextCase - 1) {
//                // Oh dear.  This is going to need some moving around.
//                throw new ConfusedCFRException("Case statement doesn't cover expected range.");
//            }
            indexLastInLastBlock = indexLastInThis;
            for (int y = indexThisCase + 1; y <= indexLastInThis; ++y) {
                Op03SimpleStatement statement = statements.get(y);
                statement.markBlock(caseBlock);
                statement.markBlock(switchBlock);
                if (statement.getJumpType().isUnknown()) {
                    for (Op03SimpleStatement innerTarget : statement.getTargets()) {
                        innerTarget = Misc.followNopGoto(innerTarget, false, false);
                        if (nextCaseIndex.isBackJumpFrom(innerTarget)) {
                            if (!innerTarget.getBlockIdentifiers().contains(switchBlock)) {
                                forwardTargets.add(innerTarget);
                            }
                        }
                    }
                }
            }

            if (pullCodeIntoCase) {
                /*
                 * What if the last statement jumps to something which is outside the switch block,
                 * but held ONLY in common blocks, and has no other parents?
                 * this is effectively case statement code which has been moved outside, and seems to be
                 * pretty common for Kotlin to generate.
                 *
                 * This is a pretty aggressive operation, as it will seriously alter code which has only one valid
                 * branch out of the switch, so require an option.
                 */
                Op03SimpleStatement lastStatement = statements.get(indexLastInThis);
                /*
                 * if the last statement's a GOTO (unconditional), and that target has only one source,
                 * see if we can pull it up.  Note that we COULD be more aggressive here, but we are
                 * being cautious.
                 */
                if (lastStatement.getStatement().getClass() == GotoStatement.class) {
                    Set<BlockIdentifier> others = SetFactory.newSet(caseBlock, switchBlock);
                    Op03SimpleStatement last = lastStatement;
                    Op03SimpleStatement tgt = last.getTargets().get(0);
                    InstrIndex moveTo = last.getIndex().justAfter();
                    while (tgt.getSources().size() == 1 && tgt.getTargets().size() == 1 && SetUtil.difference(lastStatement.getBlockIdentifiers(), tgt.getBlockIdentifiers()).equals(others)) {
                        tgt.setIndex(moveTo);
                        moveTo = moveTo.justAfter();
                        tgt.getBlockIdentifiers().addAll(others);
                        last = tgt;
                        tgt = last.getTargets().get(0);
                    }
                    if (last != lastStatement) {
                        if (last.getStatement().getClass() != GotoStatement.class) {
                            /*
                             * Need to change last's target to a GOTO, which jumps to last's original target.
                             */
                            Op03SimpleStatement newGoto = new Op03SimpleStatement(last.getBlockIdentifiers(), new GotoStatement(BytecodeLoc.TODO), moveTo);
                            Op03SimpleStatement originalTgt = last.getTargets().get(0);
                            last.replaceTarget(originalTgt, newGoto);
                            originalTgt.replaceSource(last, newGoto);
                            newGoto.addTarget(originalTgt);
                            newGoto.addSource(last);
                            statements.add(newGoto);
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
        int breakTarget;
        if (forwardTargets.isEmpty()) {
            for (int y = idxFirstCase; y <= indexLastInLastBlock; ++y) {
                Op03SimpleStatement statement = statements.get(y);
                statement.markBlock(switchBlock);
            }
            if (indexLastCase != indexLastInLastBlock + 1) {
                /* This means we should have reordered the code - the fact we haven't probably
                 * means the case is jumping to some common code, i.e. not following the standard
                 * pattern.
                 * This is painful to deal with - if we can duplicate the target, then we
                 * can copy it into it's 'natural' location.
                 *
                 * For now, just consider cloning returns.
                 */
                Op03SimpleStatement lastInBlock = statements.get(indexLastInLastBlock);
                Op03SimpleStatement target = statements.get(indexLastCase);
                // We expect target to be our case statement - and IT should have 1 target.
                boolean handled = false;
                Statement targetStatement = target.getStatement();
                if (targetStatement instanceof CaseStatement) {
                    caseBlock = ((CaseStatement) targetStatement).getCaseBlock();
                    Op03SimpleStatement t2 = target.getTargets().get(0);
                    Statement statement = t2.getStatement();
                    if (statement instanceof ReturnStatement || statement instanceof GotoStatement) {
                        Op03SimpleStatement dupCase = new Op03SimpleStatement(switchStatement.getBlockIdentifiers(), targetStatement, lastInBlock.getIndex().justAfter());
                        indexLastCase = indexLastInLastBlock + 1;
                        statements.add(indexLastCase, dupCase);
                        target.removeSource(switchStatement);
                        target.nopOut();
                        switchStatement.replaceTarget(target, dupCase);
                        dupCase.addSource(switchStatement);

                        Op03SimpleStatement dupStm = new Op03SimpleStatement(dupCase.getBlockIdentifiers(), statement, dupCase.getIndex().justAfter());
                        statements.add(indexLastCase+1, dupStm);
                        dupCase.addTarget(dupStm);
                        dupStm.addSource(dupCase);
                        dupStm.getBlockIdentifiers().addAll(Arrays.asList(caseBlock, switchBlock));
                        lastCase = dupCase;
                        for (Op03SimpleStatement tgt : t2.getTargets()) {
                            dupStm.addTarget(tgt);
                            tgt.addSource(dupStm);
                        }

                        handled = true;
                    }
                }
                if (!handled) {
                    throw new ConfusedCFRException("Extractable last case doesn't follow previous, and can't clone.");
                }
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
                Op03SimpleStatement retie = new Op03SimpleStatement(blockIdentifiers, new GotoStatement(BytecodeLoc.TODO), lastInThis.getIndex().justAfter());
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

        /*
         * Sanity check:
         * If we've SOMEHOW decided the break target is inside the switch, we're wrong.
         */
        if (breakStatementTarget.getBlockIdentifiers().contains(switchBlock)) return;

        /*
         * Follow the graph backwards - anything that's an effective jump to the breakstatementtarget
         * should be rewritten as such.
         */
        Set<Op03SimpleStatement> sources = Misc.followNopGotoBackwards(breakStatementTarget);


        /* Given the assumption that the statement after the switch block is the break target, can we rewrite any
         * of the exits from the switch statement to be breaks?
         */
        for (Op03SimpleStatement breakSource : sources) {
            if (breakSource.getBlockIdentifiers().contains(switchBlock)) {
                if (breakSource.getJumpType().isUnknown()) {
                    /*
                     * Make sure it's pointing at breakstatementtarget - if not repoint.
                     */
                    JumpingStatement jumpingStatement = (JumpingStatement)(breakSource.getStatement());
                    /*
                     * Worry with an if statement is if we repoint the wrong target - make sure we only
                     * repoint the jump. (Should roll this into an interface...)
                     */
                    Op03SimpleStatement originalTarget;
                    if (jumpingStatement.getClass() == IfStatement.class) {
                        if (breakSource.getTargets().size() != 2) continue;
                        originalTarget = breakSource.getTargets().get(1);
                    } else {
                        if (breakSource.getTargets().size() != 1) continue;
                        originalTarget = breakSource.getTargets().get(0);
                    }
                    if (originalTarget != breakStatementTarget) {
                        // Repoint.
                        if (originalTarget == breakSource.getLinearlyNext()
                            && originalTarget.getStatement() instanceof CaseStatement
                            && !((CaseStatement) originalTarget.getStatement()).isDefault()
                            && originalTarget.getSources().contains(switchStatement)) {
                            // An unusual fall through.
                            breakSource.replaceStatement(new Nop());
                            continue;
                        }
                        breakSource.replaceTarget(originalTarget, breakStatementTarget);
                        originalTarget.removeSource(breakSource);
                        breakStatementTarget.addSource(breakSource);
                    }

                    ((JumpingStatement) breakSource.getStatement()).setJumpType(JumpType.BREAK);
                }
            }
        }

    }

    /*
     * If we have jumps from DIFFERENT cases into the start of a new case, we need to make sure that they
     * now refer to the case statement, not the start.
     *
     * (Of course, if these are invalid jumps then they signal some kind of duff).
     */
    private static void moveJumpsToCaseStatements(Op03SimpleStatement switchStatement) {

        SwitchStatement switchStmt = (SwitchStatement)switchStatement.getStatement();
        BlockIdentifier switchBlock = switchStmt.getSwitchBlock();

        for (Op03SimpleStatement caseStatement : switchStatement.getTargets()) {
            // Test shouldn't be necessary.
            if (!(caseStatement.getStatement() instanceof CaseStatement)) continue;
            CaseStatement caseStmt = (CaseStatement)caseStatement.getStatement();
            if (switchBlock != caseStmt.getSwitchBlock()) continue;
            BlockIdentifier caseBlock = caseStmt.getCaseBlock();

            Op03SimpleStatement target = caseStatement.getTargets().get(0);

            Iterator<Op03SimpleStatement> targetSourceIt = target.getSources().iterator();
            while (targetSourceIt.hasNext()) {
                Op03SimpleStatement src = targetSourceIt.next();
                if (src == caseStatement) continue;
                Set<BlockIdentifier> blockIdentifiers = src.getBlockIdentifiers();
                if (blockIdentifiers.contains(caseBlock)) continue;
                if (!blockIdentifiers.contains(switchBlock)) continue;

                // It's a jump to the start of the block, from a different block.  It should jump
                // to the case statement instead.
                targetSourceIt.remove();
                src.replaceTarget(target, caseStatement);
                caseStatement.addSource(src);
            }
        }
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

        // Check that lastTgt has multiple FORWARD JUMPING sources.
        List<Op03SimpleStatement> forwardJumpSources = Functional.filter(lastTgt.getSources(), new Misc.IsForwardJumpTo(lastTgt.getIndex()));
        if (forwardJumpSources.size() > 1) {
            moveInternalJumpsToTerminal(switchStatement, statements, lastTgt, following, forwardJumpSources);
        }

        // And, if 'following' has a nop goto chain, and any sources of that are actually inside the switch, move them
        // BACK to following.
        Op03SimpleStatement followingTrans = Misc.followNopGotoChain(following, false, true);
        if (followingTrans != following) {
            tightenJumpsToTerminal(statements, switchBlock, following, followingTrans);
        }
    }

    private static void tightenJumpsToTerminal(List<Op03SimpleStatement> statements, BlockIdentifier switchBlock, Op03SimpleStatement following, Op03SimpleStatement followingTrans) {
        List<Op03SimpleStatement> tsource = ListFactory.newList(followingTrans.getSources());
        boolean acted = false;
        for (Op03SimpleStatement source : tsource) {
            if (source.getBlockIdentifiers().contains(switchBlock)) {
                followingTrans.removeSource(source);
                source.replaceTarget(followingTrans, following);
                following.addSource(source);
                acted = true;
            }
        }
        // We don't want this to be undone immediately, so break nop goto chains.
        if (acted) {
            Statement followingStatement = following.getStatement();
            if (followingStatement instanceof Nop) {
                following.replaceStatement(new CommentStatement(""));
            } else if (followingStatement.getClass() == GotoStatement.class) {
                following.replaceStatement(new CommentStatement(""));
                Op03SimpleStatement force = new Op03SimpleStatement(
                        following.getBlockIdentifiers(),
                        new GotoStatement(BytecodeLoc.TODO),
                        following.getSSAIdentifiers(),
                        following.getIndex().justAfter());
                Op03SimpleStatement followingTgt = following.getTargets().get(0);
                followingTgt.replaceSource(following, force);
                following.replaceTarget(followingTgt, force);
                force.addSource(following);
                force.addTarget(followingTgt);
                statements.add(force);
            }
        }
    }

    private static void moveInternalJumpsToTerminal(Op03SimpleStatement switchStatement, List<Op03SimpleStatement> statements, Op03SimpleStatement lastTgt, Op03SimpleStatement following, List<Op03SimpleStatement> forwardJumpSources) {
        int idx = statements.indexOf(lastTgt);
        if (idx == 0) return;
        if (idx >= statements.size() - 1) return;
        if (statements.get(idx + 1) != following) return;

        // For any sources which were pointing to the last target, (other than the switch statement itself),
        // we can re-point them at the instruction after it.
        for (Op03SimpleStatement forwardJumpSource : forwardJumpSources) {
            if (forwardJumpSource == switchStatement) continue;
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

    private static class NodesReachedUntil implements BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>> {
        private final Op03SimpleStatement start;
        private final Op03SimpleStatement target;
        private final Set<Op03SimpleStatement> banned;
        private boolean found = false;
        private boolean hitBanned = false;

        private final Set<Op03SimpleStatement> reaches = SetFactory.newSet();

        private NodesReachedUntil(Op03SimpleStatement start, Op03SimpleStatement target, Set<Op03SimpleStatement> banned) {
            this.start = start;
            this.target = target;
            this.banned = banned;
        }

        @Override
        public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
            if (arg1 == target) {
                found = true;
                return;
            }
            if (banned.contains(arg1)) {
                hitBanned = true;
                return;
            }
            if (reaches.add(arg1)) {
                arg2.enqueue(arg1.getTargets());
            }
        }
    }

    private static int getDefault(DecodedSwitch decodedSwitch) {
        List<DecodedSwitchEntry> jumpTargets = decodedSwitch.getJumpTargets();
        for (int idx = 0; idx< jumpTargets.size(); ++idx) {
            DecodedSwitchEntry entry = jumpTargets.get(idx);
            if (entry.hasDefault()) return idx;
        }
        return -1;
    }

    /*
     * This handles a particular code pattern seen in some embedded java
     * in which a switch references blocks that have no spatial locality at all - usually the second switch
     * in the method behaves like this, which leads me to assume it's a code re-use strategy rather than
     * an obfuscation in the specific examples I'm seeing.
     */
    private static void tryInlineRawSwitchContent(Op03SimpleStatement switchStatement, List<Op03SimpleStatement> statements) {
        /*
         * We have to assume we can tie the cases up somehow.  First attempt - assume that the default branch
         * (following nop goto) is the common end case.
         *
         * This will only work for a limited scenario:
         *
         * - Case statements do not have fall through
         * - Default is common join point ( returns possible, but case must rejoin )
         * - Individual cases are not reachable from elsewhere
         */
        RawSwitchStatement rawSwitch = (RawSwitchStatement)switchStatement.getStatement();
        int defaultIdx = getDefault(rawSwitch.getSwitchData());
        if (defaultIdx < 0) return;
        Op03SimpleStatement defaultTarget = switchStatement.getTargets().get(defaultIdx);

        Op03SimpleStatement ultTarget = Misc.followNopGotoChain(defaultTarget, true, false);

        /*
         * For each of the cases ( including default ), ensure that they join up at ultTarget, *without intersecting*
         * and without changing block content.
         */
        Set<Op03SimpleStatement> seen = SetFactory.newSet(switchStatement);
        List<NodesReachedUntil> reachedUntils = ListFactory.newList();
        for (Op03SimpleStatement target : switchStatement.getTargets()) {
            NodesReachedUntil nodesReachedUntil = new NodesReachedUntil(target, ultTarget, seen);
            reachedUntils.add(nodesReachedUntil);
            GraphVisitor<Op03SimpleStatement> gv = new GraphVisitorDFS<Op03SimpleStatement>(target, nodesReachedUntil);
            gv.process();
            if (!nodesReachedUntil.found) {
                return;
            }
            if (nodesReachedUntil.hitBanned) {
                return;
            }
            // For each of these blocks, they should have no entry points other than switchStatement, or themselves.
            Set<Op03SimpleStatement> reachedNodes = nodesReachedUntil.reaches;
            for (Op03SimpleStatement reached : reachedNodes) {
                for (Op03SimpleStatement source : reached.getSources()) {
                    if (reachedNodes.contains(source)) {
                        continue;
                    }
                    if (source == switchStatement) {
                        continue;
                    }
                    return;
                }
            }
            if (!blockIsContiguous(statements, target, reachedNodes)) {
                return;
            }
            seen.addAll(reachedNodes);
        }
        // If we've got here, then all of the content of each of the cases can be copied into the target.
        // because we've verified that they don't share any code with other blocks though, we'll just re-label them.
        InstrIndex targetIndex = switchStatement.getIndex();
        Op03SimpleStatement lastStatement = null;
        Op03SimpleStatement firstStatement = null;
        for (NodesReachedUntil reached : reachedUntils) {
            Op03SimpleStatement start = reached.start;
            // We have already verified that these are contiguous.
            int idx = statements.indexOf(start);
            int max = idx + reached.reaches.size();
            for (int x = idx; x < max; ++x) {
                targetIndex = targetIndex.justAfter();
                lastStatement = statements.get(x);
                if (firstStatement == null) {
                    firstStatement = lastStatement;
                }
                lastStatement.setIndex(targetIndex);
            }
        }
        if (lastStatement == null) {
            // We never achieved anything.
            return;
        }
        // If ult target is before the switch, we need to introduce another node after the switch in replacement,
        // and redirect via that.
        if (ultTarget.getIndex().isBackJumpFrom(switchStatement)) {
            targetIndex = targetIndex.justAfter();
            Op03SimpleStatement ultTargetNew = new Op03SimpleStatement(lastStatement.getBlockIdentifiers(), new GotoStatement(BytecodeLoc.TODO), targetIndex);
            statements.add(ultTargetNew);
            ultTargetNew.addTarget(ultTarget);
            ultTarget.addSource(ultTargetNew);
            List<Op03SimpleStatement> ultSources = ListFactory.newList(ultTarget.getSources());
            seen.add(switchStatement);
            for (Op03SimpleStatement source : ultSources) {
                if (seen.contains(source)) {
                    ultTarget.removeSource(source);
                    source.replaceTarget(ultTarget, ultTargetNew);
                    ultTargetNew.addSource(source);
                }
            }
        }
        Set<BlockIdentifier> firstBlocks = firstStatement.getBlockIdentifiers();
        List<BlockIdentifier> newInFirst = SetUtil.differenceAtakeBtoList(firstBlocks, switchStatement.getBlockIdentifiers());
        Cleaner.sortAndRenumberInPlace(statements);
        switchStatement.getBlockIdentifiers().addAll(newInFirst);
    }

    private static void rewriteDuff(Op03SimpleStatement switchStatement, List<Op03SimpleStatement> statements, VariableFactory vf, DecompilerComments decompilerComments) {
        BlockIdentifier switchBlock = ((SwitchStatement) switchStatement.getStatement()).getSwitchBlock();
        int indexLastInLastBlock = 0;
        // Process all but the last target.  (handle that below, as we may treat it as outside the case block
        // depending on forward targets.
        List<Op03SimpleStatement> targets = switchStatement.getTargets();
        BlockIdentifier prevBlock = null;
        BlockIdentifier nextBlock = null;
        Map<Op03SimpleStatement, List<Op03SimpleStatement>> badSrcMap = MapFactory.newOrderedMap();
        for (Op03SimpleStatement cas : targets) {
            // This can only have legitimate sources of the preceeding switch block (linearly preceeding),
            // the switch statements itself, or (in theory) another statement in the same block.
            // anything else is duffesque.
            Statement casStm = cas.getStatement();
            if (!(casStm instanceof CaseStatement)) {
                continue;
            }
            CaseStatement caseStm = (CaseStatement)casStm;
            BlockIdentifier caseBlock = caseStm.getCaseBlock();
            prevBlock = nextBlock;
            nextBlock = caseBlock;
            List<Op03SimpleStatement> badSources = null;
            for (Op03SimpleStatement casSrc : cas.getSources()) {
                if (casSrc == switchStatement) continue;
                if (casSrc.getBlockIdentifiers().contains(caseBlock)) continue;
                if (casSrc.getBlockIdentifiers().contains(prevBlock)) continue;
                // strictly speaking, this is not valid, but because we pull defaults out, we can handle it.
                if (caseStm.isDefault()) continue;
                // casSrc illegally jumps to cas.
                // we can fix this by jumping to the switch, and faking the thing we switch on.
                if (badSources == null) {
                    badSources = ListFactory.newList();
                }
                badSources.add(casSrc);
            }
            if (badSources != null) {
                badSrcMap.put(cas, badSources);
            }
        }
        if (badSrcMap.isEmpty()) return;

        // Ok - we can un-duff this.  Redirect all jumps to cases to be jumps to the switch, and
        // add an additional control
        LValue intermed = vf.tempVariable(new InferredJavaType(RawJavaType.INT, InferredJavaType.Source.TRANSFORM));
        // We need to find a value which ISN'T a valid source.
        Set<Integer> iVals = SetFactory.newSortedSet();
        for (Op03SimpleStatement cas : targets) {
            // This can only have legitimate sources of the preceeding switch block (linearly preceeding),
            // the switch statements itself, or (in theory) another statement in the same block.
            // anything else is duffesque.
            Statement casStm = cas.getStatement();
            if (!(casStm instanceof CaseStatement)) {
                continue;
            }
            CaseStatement caseStm = (CaseStatement) casStm;
            List<Expression> values = caseStm.getValues();
            for (Expression e : values) {
                Literal l = e.getComputedLiteral(MapFactory.<LValue, Literal>newMap());
                if (l == null) return;
                iVals.add(l.getValue().getIntValue());
            }
        }
        Integer prev = null;
        Integer testValue = null;
        if (!iVals.contains(0)) {
            testValue = 0;
        } else {
            for (Integer i : iVals) {
                if (prev == null) {
                    if (i > Integer.MIN_VALUE) {
                        testValue = Integer.MIN_VALUE;
                        break;
                    } else {
                        prev = i;
                    }
                } else {
                    if (prev - i > 1) {
                        testValue = i-1;
                        break;
                    }
                }
            }
        }
        if (testValue == null) return;
        Literal testVal = new Literal(TypedLiteral.getInt(testValue));
        Op03SimpleStatement newPreSwitch = new Op03SimpleStatement(switchStatement.getBlockIdentifiers(), new AssignmentSimple(BytecodeLoc.NONE, intermed, testVal), switchStatement.getIndex().justBefore());
        statements.add(newPreSwitch);
        List<Op03SimpleStatement> switchStatementSources = switchStatement.getSources();
        for (Op03SimpleStatement source : switchStatementSources) {
            source.replaceTarget(switchStatement, newPreSwitch);
            newPreSwitch.addSource(source);
        }
        newPreSwitch.addTarget(switchStatement);
        switchStatementSources.clear();
        switchStatementSources.add(newPreSwitch);
        SwitchStatement swatch = (SwitchStatement)switchStatement.getStatement();
        Expression e = swatch.getSwitchOn();

        e = new TernaryExpression(BytecodeLoc.NONE, new ComparisonOperation(BytecodeLoc.NONE, new LValueExpression(intermed), testVal, CompOp.EQ), e, new LValueExpression(intermed));
        swatch.setSwitchOn(e);

        Set<Op03SimpleStatement> switchContent = Misc.GraphVisitorBlockReachable.getBlockReachable(switchStatement, switchBlock);
        Op03SimpleStatement last = Misc.getLastInRangeByIndex(switchContent);

        Op03SimpleStatement afterLast = last.getLinearlyNext();

        // After the switch, we need a
        // while (magic != missing).  This is what all the other statements will jump to.
        // (rather than continuing directly).

        Op03SimpleStatement newPostSwitch = new Op03SimpleStatement(afterLast.getBlockIdentifiers(), new IfStatement(BytecodeLoc.NONE, new BooleanExpression(Literal.TRUE)), afterLast.getIndex().justBefore());
        newPostSwitch.addTarget(afterLast);
        newPostSwitch.addTarget(switchStatement);
        afterLast.addSource(newPostSwitch);
        switchStatement.addSource(newPostSwitch);
        statements.add(newPostSwitch);

        Op03SimpleStatement newBreak = new Op03SimpleStatement(newPostSwitch.getBlockIdentifiers(), new GotoStatement(BytecodeLoc.NONE), newPostSwitch.getIndex().justBefore());
        List<Op03SimpleStatement> postSources = ListFactory.newList(afterLast.getSources());
        for (Op03SimpleStatement source : postSources) {
            if (source.getBlockIdentifiers().contains(switchBlock)) {
                source.replaceTarget(afterLast, newBreak);
                newBreak.addSource(source);
                afterLast.removeSource(source);
            }
        }
        if (!newBreak.getSources().isEmpty()) {
            newBreak.addTarget(afterLast);
            afterLast.addSource(newBreak);
            statements.add(newBreak);
        }

        for (Map.Entry<Op03SimpleStatement, List<Op03SimpleStatement>> entry : badSrcMap.entrySet()) {
            Op03SimpleStatement cas = entry.getKey();
            CaseStatement caseStatement = (CaseStatement)cas.getStatement();
            List<Expression> values = caseStatement.getValues();
            if (values.isEmpty()) continue;
            Expression oneValue = values.get(0);
            for (Op03SimpleStatement src : entry.getValue()) {
                cas.removeSource(src);
                Op03SimpleStatement newPreJump = new Op03SimpleStatement(src.getBlockIdentifiers(), new AssignmentSimple(BytecodeLoc.NONE, intermed, oneValue), src.getIndex().justBefore());
                statements.add(newPreJump);
                List<Op03SimpleStatement> srcSources = src.getSources();
                for (Op03SimpleStatement srcSrc : srcSources) {
                    srcSrc.replaceTarget(src, newPreJump);
                    newPreJump.addSource(srcSrc);
                }
                srcSources.clear();
                srcSources.add(newPreJump);
                newPreJump.addTarget(src);
                src.replaceTarget(cas, newPostSwitch);
                newPostSwitch.addSource(src);
            }
        }
        decompilerComments.addComment(DecompilerComment.DUFF_HANDLING);
    }
}
