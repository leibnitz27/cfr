package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentPreMutation;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.functors.Predicate;

import java.util.List;

class PrePostchangeAssignmentRewriter {

    /*
     * preChange is --x / ++x.
     *
     * Can we find an immediate guaranteed TEMPORARY dominator which takes the previous value of x?
     *
     * ie
     *
     * v0 = x
     * ++x
     *
     * -->
     *
     * v0 = x++
     */
    private static boolean pushPreChange(Op03SimpleStatement preChange, boolean back) {
        AssignmentPreMutation mutation = (AssignmentPreMutation) preChange.getStatement();
        Op03SimpleStatement current = preChange;

        LValue mutatedLValue = mutation.getCreatedLValue();
        Expression lvalueExpression = new LValueExpression(mutatedLValue);
        UsageWatcher usageWatcher = new UsageWatcher(mutatedLValue);

        while (true) {
            List<Op03SimpleStatement> candidates = back ? current.getSources() : current.getTargets();
            if (candidates.size() != 1) return false;

            current = candidates.get(0);
            /*
             * If current makes use of x in any way OTHER than a simple assignment, we have to abort.
             * Otherwise, if it's v0 = x, it's a candidate.
             */
            Statement innerStatement = current.getStatement();
            if (innerStatement instanceof AssignmentSimple) {
                AssignmentSimple assignmentSimple = (AssignmentSimple) innerStatement;
                if (assignmentSimple.getRValue().equals(lvalueExpression)) {
                    LValue tgt = assignmentSimple.getCreatedLValue();
                    tgt.applyExpressionRewriter(usageWatcher, null, current, ExpressionRewriterFlags.LVALUE);
                    if (usageWatcher.isFound()) {
                        return false;
                    }

                    /*
                     * Verify that the saident of tgt does not change.
                     */
                    SSAIdentifiers<LValue> preChangeIdents = preChange.getSSAIdentifiers();
                    SSAIdentifiers<LValue> assignIdents = current.getSSAIdentifiers();
                    if (!preChangeIdents.isValidReplacement(tgt, assignIdents)) {
                        return false;
                    }
                    if (back) {
                        assignIdents.setKnownIdentifierOnExit(mutatedLValue, preChangeIdents.getSSAIdentOnExit(mutatedLValue));
                    } else {
                        assignIdents.setKnownIdentifierOnEntry(mutatedLValue, preChangeIdents.getSSAIdentOnEntry(mutatedLValue));
                    }
                    current.replaceStatement(new AssignmentSimple(tgt, back ? mutation.getPostMutation() : mutation.getPreMutation()));
                    preChange.nopOut();
                    return true;
                }
            }
            current.rewrite(usageWatcher);
            if (usageWatcher.isFound()) {
                /*
                 * Found a use "Before" assignment.
                 */
                return false;
            }
        }
    }

    private static class UsageWatcher extends AbstractExpressionRewriter {
        private final LValue needle;
        boolean found = false;

        private UsageWatcher(LValue needle) {
            this.needle = needle;
        }

        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (needle.equals(lValue)) found = true;
            return super.rewriteExpression(lValue, ssaIdentifiers, statementContainer, flags);
        }

        public boolean isFound() {
            return found;
        }
    }



    private static class StatementCanBePostMutation implements Predicate<Op03SimpleStatement> {
        @Override
        public boolean test(Op03SimpleStatement in) {
            AssignmentPreMutation assignmentPreMutation = (AssignmentPreMutation) in.getStatement();
            LValue lValue = assignmentPreMutation.getCreatedLValue();
            return (assignmentPreMutation.isSelfMutatingOp1(lValue, ArithOp.PLUS) ||
                    assignmentPreMutation.isSelfMutatingOp1(lValue, ArithOp.MINUS));
        }
    }

    static void pushPreChangeBack(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> assignments = Functional.filter(statements, new TypeFilter<AssignmentPreMutation>(AssignmentPreMutation.class));
        assignments = Functional.filter(assignments, new StatementCanBePostMutation());
        if (assignments.isEmpty()) return;

        for (Op03SimpleStatement assignment : assignments) {
            if (!pushPreChange(assignment, true)) {
                pushPreChange(assignment, false);
            }
        }
    }


    /* We're searching for something a bit too fiddly to use wildcards on,
     * so lots of test casting :(
     */
    private static boolean replacePreChangeAssignment(Op03SimpleStatement statement) {
        AssignmentSimple assignmentSimple = (AssignmentSimple) statement.getStatement();

        LValue lValue = assignmentSimple.getCreatedLValue();

        // Is it an arithop
        Expression rValue = assignmentSimple.getRValue();
        if (!(rValue instanceof ArithmeticOperation)) return false;

        // Which is a mutation
        ArithmeticOperation arithmeticOperation = (ArithmeticOperation) rValue;
        if (!arithmeticOperation.isMutationOf(lValue)) return false;

        // Create an assignment prechange with the mutation
        AbstractMutatingAssignmentExpression mutationOperation = arithmeticOperation.getMutationOf(lValue);

        AssignmentPreMutation res = new AssignmentPreMutation(lValue, mutationOperation);
        statement.replaceStatement(res);
        return true;
    }


    /*
     * vX = ?
     * ? = vX + 1
     *
     * -->
     *
     * vX = ?++
     */
    private static void replacePostChangeAssignment(Op03SimpleStatement statement) {
        AssignmentSimple assignmentSimple = (AssignmentSimple) statement.getStatement();
        LValue postIncLValue = assignmentSimple.getCreatedLValue();

        if (statement.getSources().size() != 1) return;

        Op03SimpleStatement prior = statement.getSources().get(0);
        Statement statementPrior = prior.getStatement();
        if (!(statementPrior instanceof AssignmentSimple)) return;

        AssignmentSimple assignmentSimplePrior = (AssignmentSimple) statementPrior;
        LValue tmp = assignmentSimplePrior.getCreatedLValue();
        if (!(tmp instanceof StackSSALabel)) return;

        if (!assignmentSimplePrior.getRValue().equals(new LValueExpression(postIncLValue))) return;

        StackSSALabel tmpStackVar = (StackSSALabel) tmp;
        Expression stackValue = new StackValue(tmpStackVar);
        Expression incrRValue = assignmentSimple.getRValue();

        if (!(incrRValue instanceof ArithmeticOperation)) return;
        ArithmeticOperation arithOp = (ArithmeticOperation) incrRValue;
        ArithOp op = arithOp.getOp();
        if (!(op.equals(ArithOp.PLUS) || op.equals(ArithOp.MINUS))) return;

        Expression lhs = arithOp.getLhs();
        Expression rhs = arithOp.getRhs();
        if (stackValue.equals(lhs)) {
            if (!Literal.equalsAnyOne(rhs)) return;
        } else if (stackValue.equals(rhs)) {
            if (!Literal.equalsAnyOne(lhs)) return;
            if (op.equals(ArithOp.MINUS)) return;
        } else {
            return;
        }

        ArithmeticPostMutationOperation postMutationOperation = new ArithmeticPostMutationOperation(postIncLValue, op);
        prior.nopOut();
        statement.replaceStatement(new AssignmentSimple(tmp, postMutationOperation));
    }

    static void replacePrePostChangeAssignments(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> assignments = Functional.filter(statements, new TypeFilter<AssignmentSimple>(AssignmentSimple.class));
        for (Op03SimpleStatement assignment : assignments) {
            if (replacePreChangeAssignment(assignment)) continue;
            replacePostChangeAssignment(assignment);
        }
    }

}
