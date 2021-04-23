package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
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
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollectorSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.util.MiscUtils;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.Collections;
import java.util.LinkedList;
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

    private InlineDeAssigner() {
    }

    private class Deassigner extends AbstractExpressionRewriter {

        Set<LValue> read = SetFactory.newSet();
        Set<LValue> write = SetFactory.newSet();

        List<AssignmentExpression> extracted = ListFactory.newList();

        boolean noFurther = false;


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

            // We could be called by a client without the correct dispatching - normally that won't matter but
            // we need to make sure we never descend an optional term.
            if (expression instanceof ConditionalExpression) return rewriteExpression((ConditionalExpression)expression, ssaIdentifiers, statementContainer, flags);

            /* We can pull this out if none of the RHS has yet been modified.
             * the LHS has not yet been read.
             */
            if (expression instanceof AssignmentExpression) {
                AssignmentExpression assignmentExpression = (AssignmentExpression)expression;
                assignmentExpression.applyRValueOnlyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
                return tryExtractAssignment((AssignmentExpression)expression);
            }
            /*
             * Again - never descend an optional term.
             */
            if (expression instanceof TernaryExpression) {
                TernaryExpression ternaryExpression = (TernaryExpression)expression;
                expression = ternaryExpression.applyConditionOnlyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
                noFurther = true;
                return expression;
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
            // TODO : ApplyLHSOnly?
            if (expression instanceof BooleanOperation) {
                BooleanOperation booleanOperation = (BooleanOperation)expression;
                ConditionalExpression lhs = booleanOperation.getLhs();
                ConditionalExpression lhs2 = rewriteExpression(lhs, ssaIdentifiers, statementContainer, flags);
                if (lhs2 != lhs) {
                    return new BooleanOperation(BytecodeLoc.TODO, lhs2, booleanOperation.getRhs(), booleanOperation.getOp());
                }
                noFurther = true;
                return expression;
            } else {
                return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
            }
        }

        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            switch (flags) {
                case LVALUE:
                    write.add(lValue);
                    break;
                case RVALUE:
                    read.add(lValue);
                    break;
                case LANDRVALUE:
                    write.add(lValue);
                    read.add(lValue);
                    break;
            }
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
            AssignmentSimple assignmentSimple = new AssignmentSimple(BytecodeLoc.TODO, expression.getlValue(), expression.getrValue());
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


    /* We don't want to stop a slew of a = b = c = fred
     * only a = ( b = 12 ) > (c = 43)
     * So should descend any immediate assignments first.....
     */
    private void deAssign(AssignmentSimple assignmentSimple, Op03SimpleStatement container, List<Op03SimpleStatement> added) {
        Expression rhs = assignmentSimple.getRValue();
        if (rhs instanceof LValueExpression || rhs instanceof Literal) return;
        Deassigner deassigner = new Deassigner();
        LinkedList<LValue> lValues = ListFactory.newLinkedList();
        while (rhs instanceof AssignmentExpression) {
            AssignmentExpression assignmentExpression = (AssignmentExpression)rhs;
            lValues.addFirst(assignmentExpression.getlValue());
            rhs = assignmentExpression.getrValue();
        }

        Expression rhs2 = deassigner.rewriteExpression(rhs, container.getSSAIdentifiers(), container, ExpressionRewriterFlags.RVALUE);
        if (deassigner.extracted.isEmpty()) return;
        for (LValue outer : lValues) {
            rhs2 = new AssignmentExpression(BytecodeLoc.TODO, outer, rhs2);
        }
        assignmentSimple.setRValue(rhs2);
        rewrite(deassigner, container, added);
    }

    private void deAssign(Op03SimpleStatement container, List<Op03SimpleStatement> added) {
        Deassigner deassigner = new Deassigner();
        container.rewrite(deassigner);
        rewrite(deassigner, container, added);
    }

    public static void extractAssignments(List<Op03SimpleStatement> statements) {
        InlineDeAssigner inlineDeAssigner = new InlineDeAssigner();
        List<Op03SimpleStatement> newStatements = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            if (statement.getSources().size() != 1) continue;
            Statement stmt = statement.getStatement();
            Class<? extends Statement> clazz = stmt.getClass();
            if (clazz == AssignmentSimple.class) {
                inlineDeAssigner.deAssign((AssignmentSimple) stmt, statement, newStatements);
            } else if (clazz == WhileStatement.class) {
                // skip. (just looks better!)
                MiscUtils.handyBreakPoint();
            } else {
                inlineDeAssigner.deAssign(statement, newStatements);
            }
        }
        if (newStatements.isEmpty()) return;
        statements.addAll(newStatements);
        Cleaner.sortAndRenumberInPlace(statements);
    }
}
