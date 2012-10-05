package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredFor;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:05
 * To change this template use File | Settings | File Templates.
 */
public class ForStatement extends AbstractStatement {
    private ConditionalExpression condition;
    private BlockIdentifier blockIdentifier;
    private AssignmentSimple initial;
    private AbstractAssignmentExpression assignment;

    public ForStatement(ConditionalExpression conditionalExpression, BlockIdentifier blockIdentifier, AssignmentSimple initial, AbstractAssignmentExpression assignment) {
        this.condition = conditionalExpression;
        this.blockIdentifier = blockIdentifier;
        this.initial = initial;
        this.assignment = assignment;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("for (" + (initial == null ? "" : initial) + ";" + condition.toString() + "; " + assignment + ") ");
        dumper.print(" // ends " + getTargetStatement(1).getContainer().getLabel() + ";\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        throw new UnsupportedOperationException("Shouldn't be called here.");
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        condition = expressionRewriter.rewriteExpression(condition, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
        assignment = expressionRewriter.rewriteExpression(assignment, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredFor(condition, blockIdentifier, initial, assignment);
    }

    public BlockIdentifier getBlockIdentifier() {
        return blockIdentifier;
    }

    public ConditionalExpression getCondition() {
        return condition;
    }

    public AssignmentSimple getInitial() {
        return initial;
    }

    public AbstractAssignmentExpression getAssignment() {
        return assignment;
    }
}
