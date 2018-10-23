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
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollectorSimple;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.collections.SetUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class RemoveDeterministicJumps {

    public static List<Op03SimpleStatement> apply(Method method, List<Op03SimpleStatement> statements) {
        boolean success = false;
        Set<BlockIdentifier> ignoreInThese = FinallyRewriter.getBlocksAffectedByFinally(statements);

        for (Op03SimpleStatement stm : statements) {
            if (!(stm.getStatement() instanceof AssignmentSimple)) continue;
            if (SetUtil.hasIntersection(ignoreInThese, stm.getBlockIdentifiers())) continue;
            Map<LValue, Literal> display = MapFactory.newMap();
            success |= propagateLiteralReturn(method, stm, display);
        }

        if (success) {
            statements = Cleaner.removeUnreachableCode(statements, true);
        }
        return statements;
    }

    private static boolean propagateLiteralReturn(@SuppressWarnings("unused") Method method,
                                                  Op03SimpleStatement original, Map<LValue, Literal> display) {
        Op03SimpleStatement current = original;
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        int nAssigns = 0;

        boolean adjustedOrig = false;
        int nAssignsAtAdjust = 0;
        do {
            if (current.getSources().size() != 1) break;
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
                cls == MonitorExitStatement.class ||
                cls == CaseStatement.class) {
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
 * We require a straight through route, with no chances that any side effects have occured.
 * (i.e. call any methods, change members).
 * However, we can cope with further LITERAL assignments to locals and stackvars, and even
 * conditionals on them (if literal).
 *
 * This allows us to cope with the disgusting scala pattern.
 *
 * if (temp1) {
 *   ALSO JUMP HERE FROM ELSEWHERE
 *   temp2 = true;
 * } else {
 *   temp2 = false;
 * }
 * if (temp2) {
 *   temp3 = true;
 * } else {
 *   temp3 = false;
 * }
 * return temp3;
 *
 */
    private static boolean propagateLiteralReturn(@SuppressWarnings("unused") Method method, Op03SimpleStatement original, final Op03SimpleStatement orignext, final LValue originalLValue, final Expression originalRValue, Map<LValue, Literal> display) {
        Op03SimpleStatement current = orignext;
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        do {
            if (!seen.add(current)) return false;
            Class<?> cls = current.getStatement().getClass();
            List<Op03SimpleStatement> curTargets = current.getTargets();
            int nTargets = curTargets.size();
            if (cls == Nop.class) {
                if (nTargets != 1) return false;
                current = curTargets.get(0);
                continue;
            }
            if (cls == ReturnNothingStatement.class) break;
            if (cls == ReturnValueStatement.class) break;
            if (cls == GotoStatement.class ||
                    cls == MonitorExitStatement.class) {
                if (nTargets != 1) return false;
                current = curTargets.get(0);
                continue;
            }
            if (cls == AssignmentSimple.class) {
                AssignmentSimple assignmentSimple = (AssignmentSimple) current.getStatement();
                LValue lValue = assignmentSimple.getCreatedLValue();
                if (!(lValue instanceof StackSSALabel || lValue instanceof LocalVariable)) return false;
                Literal literal = assignmentSimple.getRValue().getComputedLiteral(display);
                if (literal == null) return false;
                display.put(lValue, literal);
                current = curTargets.get(0);
                continue;
            }

            // We /CAN/ actually cope with a conditional, if we're 100% sure of where we're going!
            if (cls == IfStatement.class) {
                IfStatement ifStatement = (IfStatement) current.getStatement();
                Literal literal = ifStatement.getCondition().getComputedLiteral(display);
                Boolean bool = literal == null ? null : literal.getValue().getMaybeBoolValue();
                if (bool == null) {
                    return false;
                }
                current = curTargets.get(bool ? 1 : 0);
                continue;
            }
            return false;
        } while (true);

        Class<?> cls = current.getStatement().getClass();
        /*
         * If the original rValue is a literal, we can replace.  If not, we can't.
         */
        if (cls == ReturnNothingStatement.class) {
            if (!(originalRValue instanceof Literal)) return false;
            original.replaceStatement(new ReturnNothingStatement());
            orignext.removeSource(original);
            original.removeTarget(orignext);
            return true;
        }
        /*
         * Heuristic of doom.  If the ORIGINAL rvalue is a literal (i.e. side effect free), we can
         * ignore it, and replace the original assignment with the computed literal.
         *
         * If the original rvalue is NOT a literal, AND we are returning the original lValue, we can
         * return the original rValue. (This is why we can't have side effects during the above).
         */
        if (cls == ReturnValueStatement.class) {
            ReturnValueStatement returnValueStatement = (ReturnValueStatement) current.getStatement();
            if (originalRValue instanceof Literal) {
                Expression e = returnValueStatement.getReturnValue().getComputedLiteral(display);
                if (e == null) return false;
                original.replaceStatement(new ReturnValueStatement(e, returnValueStatement.getFnReturnType()));
            } else {
                Expression ret = returnValueStatement.getReturnValue();
                if (!(ret instanceof LValueExpression)) return false;
                LValue retLValue = ((LValueExpression) ret).getLValue();
                if (!retLValue.equals(originalLValue)) return false;
                // NB : we don't have to clone rValue, as we're replacing the statement it came from.
                original.replaceStatement(new ReturnValueStatement(originalRValue, returnValueStatement.getFnReturnType()));
            }
            orignext.removeSource(original);
            original.removeTarget(orignext);
            return true;
        }
        return false;
    }

    /*
     * VERY aggressive options for simplifying control flow, at the cost of changing the appearance.
     *
     */
    public static void propagateToReturn(Method method, List<Op03SimpleStatement> statements) {
        boolean success = false;

        List<Op03SimpleStatement> assignmentSimples = Functional.filter(statements, new TypeFilter<AssignmentSimple>(AssignmentSimple.class));
        Set<BlockIdentifier> affectedByFinally = FinallyRewriter.getBlocksAffectedByFinally(statements);

        for (Op03SimpleStatement stm : assignmentSimples) {
            if (SetUtil.hasIntersection(affectedByFinally, stm.getBlockIdentifiers())) continue;
            Statement inner = stm.getStatement();
            /*
             * This pass helps with scala and dex2jar style output - find a remaining assignment to a stack
             * variable (or POSSIBLY a local), and follow it through.  If nothing intervenes, and we hit a return, we can
             * simply replace the entry point.
             *
             * We agressively attempt to follow through computable literals.
             *
             * Note that we pull this one out here, because it can handle a non-literal assignment -
             * inside PLReturn we can only handle subsequent literal assignments.
             */
            if (stm.getTargets().size() != 1)
                continue; // shouldn't be possible to be other, but a pruning might have removed.
            AssignmentSimple assignmentSimple = (AssignmentSimple) inner;
            LValue lValue = assignmentSimple.getCreatedLValue();
            Expression rValue = assignmentSimple.getRValue();
            if (!(lValue instanceof StackSSALabel || lValue instanceof LocalVariable)) continue;
            Map<LValue, Literal> display = MapFactory.newMap();
            if (rValue instanceof Literal) {
                display.put(lValue, (Literal) rValue);
            }
            Op03SimpleStatement next = stm.getTargets().get(0);
            success |= propagateLiteralReturn(method, stm, next, lValue, rValue, display);
            // Note - we can't have another go with return back yet, as it would break ternary discovery.
        }


        if (success) Op03SimpleStatement.replaceReturningIfs(statements, true);
    }

}
