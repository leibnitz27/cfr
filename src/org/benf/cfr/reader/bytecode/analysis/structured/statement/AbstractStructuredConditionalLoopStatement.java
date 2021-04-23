package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.state.TypeUsageCollector;

import java.util.List;

// do / while.
public abstract class AbstractStructuredConditionalLoopStatement extends AbstractStructuredBlockStatement {
    protected ConditionalExpression condition;
    protected final BlockIdentifier block;

    AbstractStructuredConditionalLoopStatement(BytecodeLoc loc, ConditionalExpression condition, BlockIdentifier block, Op04StructuredStatement body) {
        super(loc, body);
        this.condition = condition;
        this.block = block;
    }

    public BlockIdentifier getBlock() {
        return block;
    }

    public ConditionalExpression getCondition() {
        return condition;
    }

    @Override
    public BlockIdentifier getBreakableBlockOrNull() {
        return block;
    }

    @Override
    public boolean supportsBreak() {
        return true;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collectFrom(condition);
        super.collectTypeUsages(collector);
    }

    public boolean isInfinite() {
        return condition == null;
    }

    @Override
    public boolean isScopeBlock() {
        return true;
    }

    @Override
    public boolean supportsContinueBreak() {
        return true;
    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        if (condition != null) {
            condition.collectUsedLValues(scopeDiscoverer);
        }
        scopeDiscoverer.processOp04Statement(getBody());
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        getBody().linearizeStatementsInto(out);
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        if (condition != null) {
            condition = expressionRewriter.rewriteExpression(condition, null, this.getContainer(), null);
        }
    }

}
