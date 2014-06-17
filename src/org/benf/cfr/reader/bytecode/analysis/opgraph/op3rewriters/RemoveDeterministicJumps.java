package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollectorSimple;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class RemoveDeterministicJumps {
    public static void apply(Method method, List<Op03SimpleStatement> statements) {
        boolean success = false;

        for (Op03SimpleStatement stm : statements) {
            if (!(stm.getStatement() instanceof AssignmentSimple)) continue;
            Map<LValue, Literal> display = MapFactory.newMap();
            success |= propagateLiteralReturn(method, stm, display);
        }

        if (success) Op03SimpleStatement.removeUnreachableCode(statements, true);
    }

    private static boolean propagateLiteralReturn(Method method, Op03SimpleStatement original, Map<LValue, Literal> display) {
        Op03SimpleStatement current = original;
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        boolean canRewrite = true;
        int nAssigns = 0;

        boolean adjustedOrig = false;
        int nAssignsAtAdjust = 0;
        do {
            if (current.getSources().size() != 1) canRewrite = false;
            if (!seen.add(current)) return false; // Hit a cycle, can't help.
            Class<?> cls = current.getStatement().getClass();
            List<Op03SimpleStatement> curTargets = current.getTargets();
            int nTargets = curTargets.size();
            if (cls == Nop.class) {
                if (nTargets != 1) break;
                current = curTargets.get(0);
                continue;
            }
            if (cls == GotoStatement.class ||
                    cls == MonitorExitStatement.class) {
                if (nTargets != 1) break;
                current = curTargets.get(0);
                continue;
            }
            if (cls == AssignmentSimple.class) {
                AssignmentSimple assignmentSimple = (AssignmentSimple) current.getStatement();
                LValue lValue = assignmentSimple.getCreatedLValue();
                if (!(lValue instanceof StackSSALabel || lValue instanceof LocalVariable)) break;
                Literal literal = assignmentSimple.getRValue().getComputedLiteral(display);
                if (literal == null) break;
                display.put(lValue, literal);
                current = curTargets.get(0);
                nAssigns++;
                continue;
            }

            // We /CAN/ actually cope with a conditional, if we're 100% sure of where we're going!
            if (cls == IfStatement.class) {
                IfStatement ifStatement = (IfStatement) current.getStatement();
                Literal literal = ifStatement.getCondition().getComputedLiteral(display);
                Boolean bool = literal == null ? null : literal.getValue().getMaybeBoolValue();
                if (bool == null) {
                    // Ok - we don't know where this will go.  But (if the conditional is side effect free!)
                    // we can rely on the display being correct still.  In this case, we can see if the jump point
                    // ends us up somewhere useful.   Note that we can only do this once.
                    if (adjustedOrig) break;

                    // TODO : IF SIDE-EFFECTING /MUST/ BREAK.
                    adjustedOrig = true;
                    nAssignsAtAdjust = nAssigns;
                    original = current;
                    current = curTargets.get(1);
                    continue;
                }
                current = curTargets.get(bool ? 1 : 0);
                continue;
            }
            break;
        } while (true);

        Statement currentStatement = current.getStatement();
        Class<?> cls = current.getStatement().getClass();

        /*
         * Origin at this point could be a literal assignment statement, or a conditional.
         */
        /*
         * We have ended up at current.  If current is a return, we could copy it into our original.  If not, we could
         * create a direct jump after our original.
         *
         * If we create a jump, we need to make sure our original performs all the assignments needed to create 'display'.
         */
        if (currentStatement instanceof ReturnStatement) {

            /* Everything rewritable on the adjust list can be replaced with a return */
            if (cls == ReturnNothingStatement.class) {
                replace(original, adjustedOrig, new ReturnNothingStatement());
            } else if (cls == ReturnValueStatement.class) {
                ReturnValueStatement returnValueStatement = (ReturnValueStatement) current.getStatement();

                LValueUsageCollectorSimple collectorSimple = new LValueUsageCollectorSimple();
                Expression res = returnValueStatement.getReturnValue();
                res.collectUsedLValues(collectorSimple);
                if (SetUtil.hasIntersection(display.keySet(), collectorSimple.getUsedLValues())) {
                    // We can only replace this if we won't be cutting out assignments.
                    // I.e. the display can't contain ANY of the used lvalues!
                    return false;
                }
                Expression lit = res.getComputedLiteral(display);
                if (lit != null) res = lit;

                replace(original, adjustedOrig, new ReturnValueStatement(res, returnValueStatement.getFnReturnType()));
            } else {
                return false;
            }
            return true;
        }

        /*
         * We've got here because we followed our literal assignment through.
         * If we've not skipped any assignments we later need, we can change the target of the original
         * to here.
         *
         * NB : We only do this for adjusted return if statements, as it messes up some other operations if we
         * (effectively) do followNopGotoChain here.
         */
        if (!adjustedOrig) return false;
        Op03SimpleStatement origTarget = original.getTargets().get(1);
        if (current == origTarget) return false;

        // We could rewrite intermediates.  Not sure if it's worth it if much has changed.
        if (nAssigns != nAssignsAtAdjust || nAssigns == 0) return false;

        original.replaceTarget(origTarget, current);
        origTarget.removeSource(original);
        current.addSource(original);
        return true;
    }

    private static void replaceConditionalReturn(Op03SimpleStatement conditional, ReturnStatement returnStatement) {
        Op03SimpleStatement originalConditionalTarget = conditional.getTargets().get(1);
        IfStatement ifStatement = (IfStatement)conditional.getStatement();
        conditional.replaceStatement(new IfExitingStatement(ifStatement.getCondition(), returnStatement));
        conditional.removeTarget(originalConditionalTarget);
        originalConditionalTarget.removeSource(conditional);
    }

    private static void replaceAssignmentReturn(Op03SimpleStatement assignment, ReturnStatement returnStatement) {
        assignment.replaceStatement(returnStatement);
        Op03SimpleStatement tgt = assignment.getTargets().get(0);
        tgt.removeSource(assignment);
        assignment.removeTarget(tgt);
    }

    private static void replace(Op03SimpleStatement source, boolean isIf, ReturnStatement returnNothingStatement) {
        if (isIf) {
            replaceConditionalReturn(source, returnNothingStatement);
        } else {
            replaceAssignmentReturn(source, returnNothingStatement);
        }
    }

    /*
     * Another type of literal propagation
     *
     * if (x == false) goto b
     * a: if (y == false) goto c
     * return FRED
     * b:
     * ..
     * y = false
     * if (p) goto a
     * ..
     * ..
     * y = true
     * if (p) goto a
     * ..
     * c:
     *
     * Above, both the 'goto a' can be replaced with either 'return FRED' or 'goto c'.
     *
     * This has the potential for making normal control flow quite ugly, so should be used
     * as a fallback mechanism.
     */
    private static boolean propagateLiteralBranch(Method method, final Op03SimpleStatement original, final Op03SimpleStatement conditional, IfStatement ifStatement, Map<LValue, Literal> display) {
        if (method.toString().equals("mpc: mpc(android.content.Context )") && original.getIndex().toString().equals("lbl20")) {
            int x = 1;
        }

        Op03SimpleStatement improvement = null;
        /* We're looking for a conditional which we can rewrite the branch on.
         * Find the target of the conditional, see if it's conditional on a hardcoded value.
         */

        Op03SimpleStatement target = conditional.getTargets().get(1);
        final Op03SimpleStatement originalConditionalTarget = target;
        /*
         * At this point, we COULD continue to walk nops, gotos and literal assignments.
         *
         * TODO : Should work for an IfExitingStatement too (actually, that needs nuking. :P )
         */
        do {
            Statement tgtStatement = target.getStatement();
            if (!(tgtStatement instanceof IfStatement)) break;
            IfStatement ifStatement2 = (IfStatement)tgtStatement;
            Literal literal = ifStatement2.getCondition().getComputedLiteral(display);
            Boolean bool = literal == null ? null : literal.getValue().getMaybeBoolValue();
            if (bool == null) break;

            target = target.getTargets().get(bool ? 1 : 0);
            improvement = target;
        } while (true);

        if (improvement == null) return false;

        /*
         * If the improvement target is a return, we can generate an ifExiting here.
         */
        Statement improvementStatement = improvement.getStatement();
        if (improvementStatement instanceof ReturnStatement) {
            conditional.removeTarget(originalConditionalTarget);
            originalConditionalTarget.removeSource(conditional);
            conditional.replaceStatement(new IfExitingStatement(ifStatement.getCondition(), (ReturnStatement)improvementStatement));
        } else {
            conditional.replaceTarget(originalConditionalTarget, improvement);
            improvement.addSource(conditional);
            originalConditionalTarget.removeSource(conditional);
        }

        return true;
    }
}
