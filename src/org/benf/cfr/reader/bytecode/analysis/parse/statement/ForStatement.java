package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredFor;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class ForStatement extends AbstractStatement {
    private ConditionalExpression condition;
    private BlockIdentifier blockIdentifier;
    private AssignmentSimple initial;
    private List<AbstractAssignmentExpression> assignments;

    public ForStatement(ConditionalExpression conditionalExpression, BlockIdentifier blockIdentifier, AssignmentSimple initial, List<AbstractAssignmentExpression> assignments) {
        this.condition = conditionalExpression;
        this.blockIdentifier = blockIdentifier;
        this.initial = initial;
        this.assignments = assignments;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("for (");
        if (initial != null) dumper.dump(initial);
        dumper.print("; ").dump(condition).print("; ");
        boolean first = true;
        for (AbstractAssignmentExpression assignment : assignments) {
            first = StringUtils.comma(first, dumper);
            dumper.dump(assignment);
        }
        dumper.print(") ");
        dumper.print(" // ends " + getTargetStatement(1).getContainer().getLabel() + ";").newln();
        return dumper;
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        for (AbstractAssignmentExpression assignment : assignments) {
            assignment.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
        }
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        condition = expressionRewriter.rewriteExpression(condition, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
        for (int i=0,len=assignments.size();i<len;++i) {
            assignments.set(i, (AbstractAssignmentExpression) expressionRewriter.rewriteExpression(assignments.get(i), ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE));
        }
    }


    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        condition.collectUsedLValues(lValueUsageCollector);
        for (AbstractAssignmentExpression assignment : assignments) {
            assignment.collectUsedLValues(lValueUsageCollector);
        }
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredFor(condition, blockIdentifier, initial, assignments);
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

    public List<AbstractAssignmentExpression> getAssignments() {
        return assignments;
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        ForStatement other = (ForStatement) o;
        if (!constraint.equivalent(condition, other.condition)) return false;
        if (!constraint.equivalent(initial, other.initial)) return false;
        if (!constraint.equivalent(assignments, other.assignments)) return false;
        return true;
    }

}
