package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.Collections;
import java.util.List;
import java.util.Set;

class WhileRewriter {

    private static void rewriteDoWhileTruePredAsWhile(Op03SimpleStatement end, List<Op03SimpleStatement> statements) {
        WhileStatement whileStatement = (WhileStatement) end.getStatement();
        if (null != whileStatement.getCondition()) return;

        /*
         * The first statement inside this loop needs to be a test which breaks out of the loop.
         */
        List<Op03SimpleStatement> endTargets = end.getTargets();
        if (endTargets.size() != 1) return;

        Op03SimpleStatement loopStart = endTargets.get(0);
        Statement loopBodyStartStatement = loopStart.getStatement();
        /*
         * loopBodyStartStatement is NOT the do Statement, but is the first statement.
         * We need to search its sources to find the DO statement, and verify that it's the
         * correct one for the block.
         */
        BlockIdentifier whileBlockIdentifier = whileStatement.getBlockIdentifier();

        Op03SimpleStatement doStart = null;
        for (Op03SimpleStatement source : loopStart.getSources()) {
            Statement statement = source.getStatement();
            if (statement.getClass() == DoStatement.class) {
                DoStatement doStatement = (DoStatement) statement;
                if (doStatement.getBlockIdentifier() == whileBlockIdentifier) {
                    doStart = source;
                    break;
                }
            }
        }
        if (doStart == null) return;

        /* Now - is the loopBodyStartStatement a conditional?
         * If it's a direct jump, we can just target the while statement.
         */
        if (loopBodyStartStatement.getClass() == IfStatement.class) {
            return; // Not handled yet.
        } else if (loopBodyStartStatement.getClass() == IfExitingStatement.class) {
            IfExitingStatement ifExitingStatement = (IfExitingStatement) loopBodyStartStatement;
            Statement exitStatement = ifExitingStatement.getExitStatement();
            ConditionalExpression conditionalExpression = ifExitingStatement.getCondition();
            WhileStatement replacementWhile = new WhileStatement(conditionalExpression.getNegated(), whileBlockIdentifier);
            GotoStatement endGoto = new GotoStatement();
            endGoto.setJumpType(JumpType.CONTINUE);
            end.replaceStatement(endGoto);
            Op03SimpleStatement after = new Op03SimpleStatement(doStart.getBlockIdentifiers(), exitStatement, end.getIndex().justAfter());

            int endIdx = statements.indexOf(end);
            if (endIdx < statements.size() - 2) {
                Op03SimpleStatement shuffled = statements.get(endIdx + 1);
                for (Op03SimpleStatement shuffledSource : shuffled.getSources()) {
                    if (shuffledSource.getStatement() instanceof JumpingStatement) {
                        JumpingStatement jumpingStatement = (JumpingStatement) shuffledSource.getStatement();
                        if (jumpingStatement.getJumpType() == JumpType.BREAK) {
                            jumpingStatement.setJumpType(JumpType.GOTO);
                        }
                    }
                }
            }
            statements.add(endIdx + 1, after);
            // Any break statements which were targetting end+1 are now invalid.....
            doStart.addTarget(after);
            after.addSource(doStart);
            doStart.replaceStatement(replacementWhile);
            /*
             * Replace everything that pointed at loopStart with a pointer to doStart.
             */
            Op03SimpleStatement afterLoopStart = loopStart.getTargets().get(0);
            doStart.replaceTarget(loopStart, afterLoopStart);
            afterLoopStart.replaceSource(loopStart, doStart);
            loopStart.removeSource(doStart);
            loopStart.removeTarget(afterLoopStart);
            for (Op03SimpleStatement otherSource : loopStart.getSources()) {
                otherSource.replaceTarget(loopStart, doStart);
                doStart.addSource(otherSource);
            }
            loopStart.getSources().clear();
            loopStart.nopOut();
            whileBlockIdentifier.setBlockType(BlockType.WHILELOOP);
            return;
        } else {
            return;
        }
    }

    static void rewriteDoWhileTruePredAsWhile(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> doWhileEnds = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return (in.getStatement() instanceof WhileStatement) && ((WhileStatement) in.getStatement()).getBlockIdentifier().getBlockType() == BlockType.UNCONDITIONALDOLOOP;
            }
        });

        if (doWhileEnds.isEmpty()) return;

        for (Op03SimpleStatement whileEnd : doWhileEnds) {
            rewriteDoWhileTruePredAsWhile(whileEnd, statements);
        }
    }

    private static Set<LValue> findForInvariants(Op03SimpleStatement start, BlockIdentifier whileLoop) {
        Set<LValue> res = SetFactory.newOrderedSet();
        Op03SimpleStatement current = start;
        while (current.getBlockIdentifiers().contains(whileLoop)) {
            /* Note that here we're checking for assignments to determine what is suitable for lifting into a
             * for postcondition.
             *
             * This means that we will find x = x | fred ; x = x.doFred(); x = x + 1; etc.
             * As such, we want to make sure that we haven't transformed assignments into ExprStatements of
             * postAdjustExpressions yet.
             */
            if (current.getStatement() instanceof AbstractAssignment) {
                AbstractAssignment assignment = (AbstractAssignment) current.getStatement();
                if (assignment.isSelfMutatingOperation()) {
                    LValue lValue = assignment.getCreatedLValue();
                    SSAIdent after = current.getSSAIdentifiers().getSSAIdentOnExit(lValue);
                    SSAIdent expected = start.getSSAIdentifiers().getSSAIdentOnEntry(lValue);
                    if (!after.equals(expected)) break;
                    res.add(lValue);
                }
            }
            if (current.getSources().size() > 1) break;
            Op03SimpleStatement next = current.getSources().get(0);
            if (!current.getIndex().isBackJumpTo(next)) break;
            current = next;
        }
        return res;
    }

    private static void rewriteWhileAsFor(Op03SimpleStatement statement, boolean aggcapture) {
        // Find the backwards jumps to this statement
        List<Op03SimpleStatement> backSources = Functional.filter(statement.getSources(), new Misc.IsBackJumpTo(statement.getIndex()));
        //
        // Determine what could be the loop invariant.
        //
        WhileStatement whileStatement = (WhileStatement) statement.getStatement();
        ConditionalExpression condition = whileStatement.getCondition();
        Set<LValue> loopVariablePossibilities = condition.getLoopLValues();
        // If we can't find a possible invariant, no point proceeding.
        if (loopVariablePossibilities.isEmpty()) {
            return;
        }

        BlockIdentifier whileBlockIdentifier = whileStatement.getBlockIdentifier();
        // For each of the back calling targets, find a CONSTANT inc/dec
        // * which is in the loop arena
        // * before any instruction which has multiple parents.
        Set<LValue> reverseOrderedMutatedPossibilities = null;
        for (Op03SimpleStatement source : backSources) {
            Set<LValue> incrPoss = findForInvariants(source, whileBlockIdentifier);
            if (reverseOrderedMutatedPossibilities == null) {
                reverseOrderedMutatedPossibilities = incrPoss;
            } else {
                reverseOrderedMutatedPossibilities.retainAll(incrPoss);
            }
            // If there are no possibilites, then we can't do anything.
            if (reverseOrderedMutatedPossibilities.isEmpty()) {
                return;
            }
        }
        //noinspection ConstantConditions
        if (reverseOrderedMutatedPossibilities == null || reverseOrderedMutatedPossibilities.isEmpty()) {
            // no invariant instruction
            return;
        }
        loopVariablePossibilities.retainAll(reverseOrderedMutatedPossibilities);
        // Intersection between incremented / tested.
        if (loopVariablePossibilities.isEmpty()) {
            // no invariant intersection
            return;
        }

        Op03SimpleStatement loopVariableOp = null;
        LValue loopVariable = null;
        for (LValue loopVariablePoss : loopVariablePossibilities) {

            //
            // If possible, go back and find an unconditional assignment to the loop variable.
            // We have to be sure that moving this to the for doesn't violate SSA versions.
            //
            Op03SimpleStatement initialValue = findMovableAssignment(statement, loopVariablePoss);
            if (initialValue != null) {
                if (loopVariableOp == null || initialValue.getIndex().isBackJumpTo(loopVariableOp)) {
                    loopVariableOp = initialValue;
                    loopVariable = loopVariablePoss;
                }
            }
        }
        if (loopVariable == null) return;
        AssignmentSimple initalAssignmentSimple = null;


        List<AbstractAssignmentExpression> postUpdates = ListFactory.newList();
        List<List<Op03SimpleStatement>> usedMutatedPossibilities = ListFactory.newList();
        boolean usesLoopVar = false;
        for (LValue otherMutant : reverseOrderedMutatedPossibilities) {
            List<Op03SimpleStatement> othermutations = getMutations(backSources, otherMutant, whileBlockIdentifier);
            if (othermutations == null) continue;

            // We abort if we're about to lift a mutation which isn't in the predicate.
            // This is not necessarily the best idea, but otherwise we might lift all sorts of stuff,
            // leading to very ugly code.
            if (!loopVariablePossibilities.contains(otherMutant)) {
                if (!aggcapture) break;
            }
            if (otherMutant.equals(loopVariable)) usesLoopVar = true;

            AbstractAssignmentExpression postUpdate2 = ((AbstractAssignment)(othermutations.get(0).getStatement())).getInliningExpression();
            postUpdates.add(postUpdate2);
            usedMutatedPossibilities.add(othermutations);
        }
        if (!usesLoopVar) return;

        Collections.reverse(postUpdates);
        for (List<Op03SimpleStatement> lst : usedMutatedPossibilities) {
            for (Op03SimpleStatement op : lst) {
                op.nopOut();
            }
        }

        //noinspection ConstantConditions
        if (loopVariableOp != null) {
            initalAssignmentSimple = (AssignmentSimple) loopVariableOp.getStatement();
            loopVariableOp.nopOut();
        }

        whileBlockIdentifier.setBlockType(BlockType.FORLOOP);

        whileStatement.replaceWithForLoop(initalAssignmentSimple, postUpdates);

        for (Op03SimpleStatement source : backSources) {
            if (source.getBlockIdentifiers().contains(whileBlockIdentifier)) {
                /*
                 * Loop at anything which jumps directly to here.
                 */
                List<Op03SimpleStatement> ssources = ListFactory.newList(source.getSources());
                for (Op03SimpleStatement ssource : ssources) {
                    if (ssource.getBlockIdentifiers().contains(whileBlockIdentifier)) {
                        Statement sstatement = ssource.getStatement();
                        if (sstatement instanceof JumpingStatement) {
                            JumpingStatement jumpingStatement = (JumpingStatement) sstatement;
                            if (jumpingStatement.getJumpTarget().getContainer() == source) {
                                ((JumpingStatement) sstatement).setJumpType(JumpType.CONTINUE);
                                ssource.replaceTarget(source, statement);
                                statement.addSource(ssource);
                                source.removeSource(ssource);
                            }
                        }
                    }
                }
            }
        }
    }

    private static Op03SimpleStatement findMovableAssignment(Op03SimpleStatement start, LValue lValue) {
        Op03SimpleStatement current = Misc.findSingleBackSource(start);
        if (current == null) {
            return null;
        }
        do {
            if (current.getStatement() instanceof AssignmentSimple) {
                AssignmentSimple assignmentSimple = (AssignmentSimple) current.getStatement();
                if (assignmentSimple.getCreatedLValue().equals(lValue)) {
                    /* Verify that everything on the RHS is at the correct version */
                    Expression rhs = assignmentSimple.getRValue();
                    LValueUsageCollectorSimple lValueUsageCollector = new LValueUsageCollectorSimple();
                    rhs.collectUsedLValues(lValueUsageCollector);
                    if (SSAIdentifierUtils.isMovableUnder(lValueUsageCollector.getUsedLValues(), lValue, start.getSSAIdentifiers(), current.getSSAIdentifiers())) {
                        return current;
                    } else {
                        // incompatible sources
                        return null;
                    }
                }
            }
            if (current.getSources().size() != 1) {
                // too many sources
                return null;
            }
            current = current.getSources().get(0);
        } while (current != null);
        return null;
    }

    // Todo - could get these out at the same time as below..... would add complexity though...
    private static Op03SimpleStatement getForInvariant(Op03SimpleStatement start, LValue invariant, BlockIdentifier whileLoop) {
        Op03SimpleStatement current = start;
        while (current.getBlockIdentifiers().contains(whileLoop)) {
            if (current.getStatement() instanceof AbstractAssignment) {
                AbstractAssignment assignment = (AbstractAssignment) current.getStatement();
                LValue assigned = assignment.getCreatedLValue();
                if (invariant.equals(assigned)) {
                    if (assignment.isSelfMutatingOperation()) return current;
                }
            }
            if (current.getSources().size() > 1) break;
            Op03SimpleStatement next = current.getSources().get(0);
            if (!current.getIndex().isBackJumpTo(next)) break;
            current = next;
        }
        throw new ConfusedCFRException("Shouldn't be able to get here.");
    }

    private static List<Op03SimpleStatement> getMutations(List<Op03SimpleStatement> backSources, LValue loopVariable, BlockIdentifier whileBlockIdentifier) {

        /*
         * Now, go back and get the list of mutations.  Make sure they're all equivalent, then nop them out.
         */
        List<Op03SimpleStatement> mutations = ListFactory.newList();
        for (Op03SimpleStatement source : backSources) {
            Op03SimpleStatement incrStatement = getForInvariant(source, loopVariable, whileBlockIdentifier);
            mutations.add(incrStatement);
        }

        Op03SimpleStatement baseline = mutations.get(0);
        for (Op03SimpleStatement incrStatement : mutations) {
            // Compare - they all have to mutate in the same way.
            if (!baseline.equals(incrStatement)) {
                // incompatible constant mutations.
                return null;
            }
        }
        return mutations;
    }

    static void rewriteWhilesAsFors(Options options, List<Op03SimpleStatement> statements) {
        // Find all the while loops beginnings.
        List<Op03SimpleStatement> whileStarts = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return (in.getStatement() instanceof WhileStatement) && ((WhileStatement) in.getStatement()).getBlockIdentifier().getBlockType() == BlockType.WHILELOOP;
            }
        });
        boolean aggcapture = options.getOption(OptionsImpl.FOR_LOOP_CAPTURE) == Troolean.TRUE;
        for (Op03SimpleStatement whileStart : whileStarts) {
            rewriteWhileAsFor(whileStart, aggcapture);
        }
    }

}
