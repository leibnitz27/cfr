package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AbstractAssignment;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollectorSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.collections.SetUtil;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.List;
import java.util.Set;

public class ConditionalCondenser {

    private boolean testEclipse;
    private boolean notInstanceOf;

    private ConditionalCondenser(boolean testEclipse, boolean notInstanceOf) {

        this.testEclipse = testEclipse;
        this.notInstanceOf = notInstanceOf;
    }

    /* If there is a chain of assignments before this conditional,
     * AND following single parents back, there is only conditionals and assignments,
     * AND this chain terminates in a back jump.....
     */
    private static boolean appropriateForIfAssignmentCollapse1(Op03SimpleStatement statement) {
        boolean extraCondSeen = false;
        boolean preCondAssignmentSeen = false;
        while (statement.getSources().size() == 1) {
            Op03SimpleStatement source = statement.getSources().get(0);
            if (source == statement) break;
            // If there's a single parent, and it's a backjump, then I'm confused, as that means
            // we have a loop with no entry point...
            if (statement.getIndex().isBackJumpFrom(source)) break;
            Statement contained = source.getStatement();
            if (contained instanceof AbstractAssignment) {
                preCondAssignmentSeen |= (!extraCondSeen);
            } else if (contained instanceof IfStatement) {
                extraCondSeen = true;
            } else {
                break;
            }
            statement = source;
        }
        if (!preCondAssignmentSeen) return false;
        // It turns out we generate better code with this, as we want (where possible) to /avoid/ pushing these
        // assignments.
        if (extraCondSeen) return false;
        /* If this statement has any backjumping sources then we consider it */
        InstrIndex statementIndex = statement.getIndex();
        for (Op03SimpleStatement source : statement.getSources()) {
            if (statementIndex.isBackJumpFrom(source)) return true;
        }
        return false;
    }

    private static boolean appropriateForIfAssignmentCollapse2(Op03SimpleStatement statement) {
        boolean preCondAssignmentSeen = false;
        while (statement.getSources().size() == 1) {
            Op03SimpleStatement source = statement.getSources().get(0);
            if (source.getTargets().size() != 1) break;
            Statement contained = source.getStatement();
            if (contained instanceof AbstractAssignment) {
                preCondAssignmentSeen = true;
            }
            statement = source;
        }
        if (!preCondAssignmentSeen) return false;
        return true;
    }

    // a=x
    // b=y
    // if (b==a)
    //
    // --> if ((b=x)==(a=y))
    private void collapseAssignmentsIntoConditional(Op03SimpleStatement ifStatement) {

        if (!(appropriateForIfAssignmentCollapse1(ifStatement) ||
                appropriateForIfAssignmentCollapse2(ifStatement))) return;
        IfStatement innerIf = (IfStatement) ifStatement.getStatement();
        ConditionalExpression conditionalExpression = innerIf.getCondition();

        /*
         * The 'verify' block stops us winding up unless we'd do it into another conditional
         * or into a backjump.
         *
         * Otherwise, we end up with lots of code like
         *
         * int x
         * if ( (x=3) < y )
         *
         * rather than
         *
         * int x = 3
         * if (x < y)
         *
         * which is (a) ugly, and (b) screws with final analysis.
         */
        /*
         * HOWEVER - eclipse (of course) generates code which looks like
         *
         *
         */
        boolean eclipseHeuristic = testEclipse && ifStatement.getTargets().get(1).getIndex().isBackJumpFrom(ifStatement);
        if (!eclipseHeuristic) {
            Op03SimpleStatement statement = ifStatement;
            Set<Op03SimpleStatement> visited = SetFactory.newSet();
            verify:
            do {
                if (statement.getSources().size() > 1) {
                    // Progress if we're a backjump target.
                    // Otherwise, we'll cause problems with assignments inside
                    // while conditionals.
                    InstrIndex statementIndex = statement.getIndex();
                    for (Op03SimpleStatement source : statement.getSources()) {
                        if (statementIndex.isBackJumpFrom(source)) {
                            break verify;
                        }
                    }
                }
                if (statement.getSources().isEmpty()) {
                    break;
                }
                statement = statement.getSources().get(0);
                if (!visited.add(statement)) {
                    return;
                }
                Statement opStatement = statement.getStatement();
                if (opStatement instanceof IfStatement) break;
                if (opStatement instanceof Nop) continue;
                if (opStatement instanceof AbstractAssignment) continue;
                return;
            } while (true);
        }

        /* where possible, collapse any single parent assignments into this. */
        Op03SimpleStatement previousSource = null;
        while (ifStatement.getSources().size() == 1) {
            Op03SimpleStatement source = ifStatement.getSources().get(0);
            if (source == previousSource) return;
            previousSource = source;
            if (!(source.getStatement() instanceof AbstractAssignment)) return;
            LValue lValue = source.getStatement().getCreatedLValue();
            if (lValue instanceof StackSSALabel) return;
            // We don't have to worry about RHS having undesired side effects if we roll it into the
            // conditional - that has already happened.
            LValueUsageCollectorSimple lvc = new LValueUsageCollectorSimple();
            // NB - this will collect values even if they are NOT guaranteed to be used
            // i.e. are on the RHS of a comparison, or in a ternary.
            conditionalExpression.collectUsedLValues(lvc);
            if (!lvc.isUsed(lValue)) return;
            AbstractAssignment assignment = (AbstractAssignment) (source.getStatement());

            AbstractAssignmentExpression assignmentExpression = assignment.getInliningExpression();
            LValueUsageCollectorSimple assignmentLVC = new LValueUsageCollectorSimple();
            assignmentExpression.collectUsedLValues(assignmentLVC);
            Set<LValue> used = SetFactory.newSet(assignmentLVC.getUsedLValues());
            used.remove(lValue);
            Set<LValue> usedComparison = SetFactory.newSet(lvc.getUsedLValues());

            // Avoid situation where we have
            // a = x
            // b = y.f(a)
            // if (a == b) <-- should not get rolled up.
            SSAIdentifiers<LValue> beforeSSA = source.getSSAIdentifiers();
            SSAIdentifiers<LValue> afterSSA = ifStatement.getSSAIdentifiers();

            Set<LValue> intersection  = SetUtil.intersectionOrNull(used, usedComparison);
            if (intersection != null) {
                // If there's an intersection, we require the ssa idents for before/after to be the same.
                for (LValue intersect : intersection) {
                    if (!afterSSA.isValidReplacement(intersect, beforeSSA)) {
                        return;
                    }
                }
            }

            if (!afterSSA.isValidReplacement(lValue, beforeSSA)) return;
            LValueAssignmentExpressionRewriter rewriter = new LValueAssignmentExpressionRewriter(lValue, assignmentExpression, source);
            ConditionalExpression replacement = rewriter.rewriteExpression(conditionalExpression, ifStatement.getSSAIdentifiers(), ifStatement, ExpressionRewriterFlags.LVALUE);
            if (replacement == null) return;
            innerIf.setCondition(replacement);
        }

    }

    /*
     * Deal with
     *
     * a=b
     * if (a==4) {
     * }
     *
     * vs
     *
     * if ((a=b)==4) {
     * }
     *
     * We will always have the former, but (ONLY!) just after a backjump, (with only conditionals and assignments, and
     * single parents), we will want to run them together.
     */
    static void collapseAssignmentsIntoConditionals(List<Op03SimpleStatement> statements, Options options, ClassFileVersion classFileVersion) {
        // find all conditionals.
        List<Op03SimpleStatement> ifStatements = Functional.filter(statements, new TypeFilter<IfStatement>(IfStatement.class));
        if (ifStatements.isEmpty()) return;

        boolean testEclipse = options.getOption(OptionsImpl.ECLIPSE);
        boolean notBeforeInstanceOf = options.getOption(OptionsImpl.INSTANCEOF_PATTERN, classFileVersion);
        ConditionalCondenser c = new ConditionalCondenser(testEclipse, notBeforeInstanceOf);
        for (Op03SimpleStatement statement : ifStatements) {
            c.collapseAssignmentsIntoConditional(statement);
        }
    }

}
