package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollectorSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/*
 * Multiple stages in the pipeline benefit from having overly aggressive inline assignments...
 * (rebuilding complex conditionals requires inline assignments to be pushed as hard as possible!)
 *
 * But this can leave us with rather ugly and overly complicated code.  Now we have made all the use
 * we will of these, pull them back out (WHERE POSSIBLE!).
 *
 * eg (bad)
 *
 *           while (i$.hasNext()) {
 *               Map.Entry<JavaRefTypeInstance, JavaGenericRefTypeInstance> entry;
 *               JavaRefTypeInstance superC;
 *               if ((superC = (entry = i$.next()).getKey()).equals(this.getClassType())) continue;
 *
 *
 * We use a very simple heuristic here - rather than searching SSA ident sets, which are liable to be messy
 * by now, we will simply abort if there are any unextractable assignments, or method calls (that we're not extracting)
 */
public class InlineDeAssigner {
    private static class Deassigner extends AbstractExpressionRewriter {

        Set<LValue> read = SetFactory.newSet();
        Set<LValue> write = SetFactory.newSet();

        List<AssignmentExpression> extracted = ListFactory.newList();

        boolean noFurther = false;
        /*
         * We need to go inside out, so recurse first.
         */
//        @Override
//        public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
//            if (noFurther) return expression;
//            return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
//        }

        /*
         * Verify that the lhs has not been used at all, and that the values in the RHS have not been
         */
        private Expression tryExtractAssignment(AssignmentExpression assignmentExpression) {
            LValue lValue = assignmentExpression.getlValue();
            if (read.contains(lValue) || write.contains(lValue)) return assignmentExpression;
            LValueUsageCollectorSimple lValueUsageCollectorSimple = new LValueUsageCollectorSimple();
            assignmentExpression.getrValue().collectUsedLValues(lValueUsageCollectorSimple);
            for (LValue lValue1 : lValueUsageCollectorSimple.getUsedLValues()) {
                if (write.contains(lValue1)) return assignmentExpression;
            }
            extracted.add(assignmentExpression);
            return new LValueExpression(lValue);
        }

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (noFurther) return expression;

            /* We can pull this out if none of the RHS has yet been modified.
             * the LHS has not yet been read.
             */
            if (expression instanceof AssignmentExpression) {
                AssignmentExpression assignmentExpression = (AssignmentExpression)expression;
                assignmentExpression.getrValue().applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
                return tryExtractAssignment((AssignmentExpression)expression);
            }
            /*
             * Need to be very... VERY careful when extracting through ternaries.
             */
            if (expression instanceof TernaryExpression) {
                noFurther = true;
            }

            Expression result = super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);

            if (expression instanceof AbstractFunctionInvokation) {
                noFurther = true;
            }
            return result;
        }

        @Override
        public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (noFurther) return expression;
            // We can only go down the LHS of a boolean -
            if (expression instanceof BooleanOperation) {
                BooleanOperation booleanOperation = (BooleanOperation)expression;
                ConditionalExpression lhs = booleanOperation.getLhs();
                ConditionalExpression lhs2 = rewriteExpression(lhs, ssaIdentifiers, statementContainer, flags);
                if (lhs2 != lhs) {
                    return new BooleanOperation(lhs2, booleanOperation.getRhs(), booleanOperation.getOp());
                }
                noFurther = true;
                return expression;
            } else {
                return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
            }
        }

        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            Set<LValue> set = flags == ExpressionRewriterFlags.LVALUE ? write : read;
            set.add(lValue);
            return lValue;
        }

        @Override
        public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            return (StackSSALabel)rewriteExpression((LValue)lValue, ssaIdentifiers, statementContainer, flags);
        }
    }


    private static void rewrite(Deassigner deassigner, Op03SimpleStatement container, List<Op03SimpleStatement> added) {
        List<AssignmentExpression> assignmentExpressions = deassigner.extracted;
        if (assignmentExpressions.isEmpty()) return;
        Collections.reverse(assignmentExpressions);
        InstrIndex index = container.getIndex();
        Op03SimpleStatement last = container;
        List<Op03SimpleStatement> sources = ListFactory.newList(container.getSources());
        container.getSources().clear();
        for (AssignmentExpression expression : assignmentExpressions) {
            index = index.justBefore();
            AssignmentSimple assignmentSimple = new AssignmentSimple(expression.getlValue(), expression.getrValue());
            Op03SimpleStatement newAssign = new Op03SimpleStatement(container.getBlockIdentifiers(), assignmentSimple, index);
            added.add(newAssign);
            newAssign.addTarget(last);
            last.addSource(newAssign);
            last = newAssign;
        }
        for (Op03SimpleStatement source : sources) {
            source.replaceTarget(container, last);
            last.addSource(source);
        }
    }

    private static void deAssign(IfStatement ifStatement, Op03SimpleStatement container, List<Op03SimpleStatement> added) {
        Deassigner deassigner = new Deassigner();
        // The outer expression can never be an assignment (that would be wrapped in a boolean expression)
        // so we don't have to push back into the if statement.
        Expression condition = ifStatement.getCondition();
        deassigner.rewriteExpression(condition, container.getSSAIdentifiers(), container, ExpressionRewriterFlags.RVALUE);
        rewrite(deassigner, container, added);
    }

    /* We don't want to stop a slew of a = b = c = fred
     * only a = ( b = 12 ) > (c = 43)
     * So should descend any immediate assignments first.....
     */
    private static void deAssign(AssignmentSimple assignmentSimple, Op03SimpleStatement container, List<Op03SimpleStatement> added) {
        Deassigner deassigner = new Deassigner();
        assignmentSimple.rewriteExpressions(deassigner, container.getSSAIdentifiers());
        rewrite(deassigner, container, added);
    }

    public static boolean extractAssignments(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> newStatements = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            Statement stmt = statement.getStatement();
            Class<? extends Statement> clazz = stmt.getClass();
            if (clazz == IfStatement.class) {
                if (statement.getSources().size() != 1) continue;
                deAssign((IfStatement)stmt, statement, newStatements);
//            } else if (clazz == AssignmentSimple.class) {
//                deAssign((AssignmentSimple)stmt, statement, newStatements);
//                continue;
            }
        }
        if (newStatements.isEmpty()) return false;
        statements.addAll(newStatements);
        Cleaner.renumberInPlace(statements);
        return true;
    }
}
