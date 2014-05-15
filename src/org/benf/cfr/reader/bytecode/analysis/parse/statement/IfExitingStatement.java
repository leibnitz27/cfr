package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.output.Dumper;

public class IfExitingStatement extends AbstractStatement {

    private ConditionalExpression condition;
    //    private Expression returnExpression;
//    private JavaTypeInstance fnReturnType;
    private Statement statement;

    public IfExitingStatement(ConditionalExpression conditionalExpression, Statement statement) {
        this.condition = conditionalExpression;
        this.statement = statement;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("if (").dump(condition).print(") ");
        statement.dump(dumper);
        return dumper;
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        Expression replacementCondition = condition.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
        if (replacementCondition != condition) {
            this.condition = (ConditionalExpression) replacementCondition;
        }
        statement.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers);
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        condition = expressionRewriter.rewriteExpression(condition, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
        statement.rewriteExpressions(expressionRewriter, ssaIdentifiers);
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        condition.collectUsedLValues(lValueUsageCollector);
        statement.collectLValueUsage(lValueUsageCollector);
    }

    @Override
    public boolean condenseWithNextConditional() {
        return false;
    }

    public ConditionalExpression getCondition() {
        return condition;
    }

    public Statement getExitStatement() {
        return statement;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredIf(condition, new Op04StructuredStatement(Block.getBlockFor(false, statement.getStructuredStatement())));
    }

    public void optimiseForTypes() {
        condition = condition.optimiseForType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IfExitingStatement that = (IfExitingStatement) o;

        if (condition != null ? !condition.equals(that.condition) : that.condition != null) return false;
        if (!statement.equals(that.statement)) return false;

        return true;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        IfExitingStatement other = (IfExitingStatement) o;
        if (!constraint.equivalent(condition, other.condition)) return false;
        if (!constraint.equivalent(statement, other.statement)) return false;
        return true;
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return condition.canThrow(caught) || statement.canThrow(caught);
    }
}
