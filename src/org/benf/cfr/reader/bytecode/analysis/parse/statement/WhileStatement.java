package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredWhile;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:05
 * To change this template use File | Settings | File Templates.
 */
public class WhileStatement extends AbstractStatement {
    private ConditionalExpression condition;
    private BlockIdentifier blockIdentifier;

    public WhileStatement(ConditionalExpression conditionalExpression, BlockIdentifier blockIdentifier) {
        this.condition = conditionalExpression;
        this.blockIdentifier = blockIdentifier;
    }

    private int getBackJumpIndex() {
        return condition == null ? 0 : 1;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("while (");
        if (condition == null) {
            dumper.print("true");
        } else {
            dumper.dump(condition);
        }
        dumper.print(") ");
        dumper.print(" // ends " + getTargetStatement(getBackJumpIndex()).getContainer().getLabel() + ";\n");
        return dumper;
    }

    public void replaceWithForLoop(AssignmentSimple initial, AbstractAssignmentExpression assignment) {
        if (condition == null) {
            throw new UnsupportedOperationException();
        }
        ForStatement forStatement = new ForStatement(condition, blockIdentifier, initial, assignment);
        getContainer().replaceStatement(forStatement);
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        if (condition == null) return;
        Expression replacementCondition = condition.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
        if (replacementCondition != condition) throw new ConfusedCFRException("Can't yet support replacing conditions");
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        if (condition == null) return;
        condition = expressionRewriter.rewriteExpression(condition, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        if (condition != null) condition.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredWhile(condition, blockIdentifier, getTargetStatement(getBackJumpIndex()).getContainer().getBlocksEnded());
    }

    public BlockIdentifier getBlockIdentifier() {
        return blockIdentifier;
    }

    public ConditionalExpression getCondition() {
        return condition;
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        WhileStatement other = (WhileStatement) o;
        if (!constraint.equivalent(condition, other.condition)) return false;
        return true;
    }

}
