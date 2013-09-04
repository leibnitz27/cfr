package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BoolOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NotOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:05
 * To change this template use File | Settings | File Templates.
 */
public class IfExitingStatement extends AbstractStatement {

    private static final int JUMP_NOT_TAKEN = 0;
    private static final int JUMP_TAKEN = 1;

    private ConditionalExpression condition;
    private Expression returnExpression;
    private JavaTypeInstance fnReturnType;


    public IfExitingStatement(ConditionalExpression conditionalExpression, Expression returnExpression, JavaTypeInstance fnReturnType) {
        this.condition = conditionalExpression;
        this.returnExpression = returnExpression;
        this.fnReturnType = fnReturnType;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("if (").dump(condition).print(") return");
        if (returnExpression != null) {
            dumper.dump(returnExpression);
        }
        dumper.endCodeln();
        return dumper;
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        Expression replacementCondition = condition.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
        if (replacementCondition != condition) {
            this.condition = (ConditionalExpression) replacementCondition;
        }
        if (returnExpression != null) {
            returnExpression = returnExpression.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
        }
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        condition = expressionRewriter.rewriteExpression(condition, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
        if (returnExpression != null) {
            returnExpression = expressionRewriter.rewriteExpression(returnExpression, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
        }
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        condition.collectUsedLValues(lValueUsageCollector);
        if (returnExpression != null) {
            returnExpression.collectUsedLValues(lValueUsageCollector);
        }
    }

    @Override
    public boolean condenseWithNextConditional() {
        return false;
    }

    public ConditionalExpression getCondition() {
        return condition;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        StructuredReturn structuredReturn;
        if (returnExpression == null) {
            structuredReturn = new StructuredReturn();
        } else {
            structuredReturn = new StructuredReturn(returnExpression, fnReturnType);
        }
        return new StructuredIf(condition, new Op04StructuredStatement(Block.getBlockFor(false, structuredReturn)));
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
        if (returnExpression != null ? !returnExpression.equals(that.returnExpression) : that.returnExpression != null)
            return false;

        return true;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        IfExitingStatement other = (IfExitingStatement) o;
        if (!constraint.equivalent(condition, other.condition)) return false;
        if (!constraint.equivalent(returnExpression, other.returnExpression)) return false;
        return true;
    }
}
