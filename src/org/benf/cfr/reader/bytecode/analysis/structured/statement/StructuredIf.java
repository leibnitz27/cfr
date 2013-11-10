package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.CanRemovePointlessBlock;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.ElseBlock;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredIf extends AbstractStructuredStatement implements CanRemovePointlessBlock {

    ConditionalExpression conditionalExpression;
    Op04StructuredStatement ifTaken;
    Op04StructuredStatement elseBlock;

    public StructuredIf(ConditionalExpression conditionalExpression, Op04StructuredStatement ifTaken) {
        this(conditionalExpression, ifTaken, null);
    }

    public StructuredIf(ConditionalExpression conditionalExpression, Op04StructuredStatement ifTaken, Op04StructuredStatement elseBlock) {
        this.conditionalExpression = conditionalExpression;
        this.ifTaken = ifTaken;
        this.elseBlock = elseBlock;
    }


    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        conditionalExpression.collectTypeUsages(collector);
        collector.collectFrom(ifTaken);
        collector.collectFrom(elseBlock);
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("if (").dump(conditionalExpression).print(") ");
        ifTaken.dump(dumper);
        if (elseBlock != null) {
            dumper.removePendingCarriageReturn();
            dumper.print(" else ");
            elseBlock.dump(dumper);
        }
        return dumper;
    }

    public boolean hasElseBlock() {
        return elseBlock != null;
    }

    public ConditionalExpression getConditionalExpression() {
        return conditionalExpression;
    }

    public Op04StructuredStatement getIfTaken() {
        return ifTaken;
    }

    @Override
    public StructuredStatement informBlockHeirachy(Vector<BlockIdentifier> blockIdentifiers) {
        ifTaken.informBlockMembership(blockIdentifiers);
        if (elseBlock != null) elseBlock.informBlockMembership(blockIdentifiers);
        return null;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
        scope.add(this);
        try {
            ifTaken.transform(transformer, scope);
            if (elseBlock != null) elseBlock.transform(transformer, scope);
        } finally {
            scope.remove(this);
        }
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        ifTaken.linearizeStatementsInto(out);
        if (elseBlock != null) {
            out.add(new ElseBlock());
            elseBlock.linearizeStatementsInto(out);
        }
    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        conditionalExpression.collectUsedLValues(scopeDiscoverer);
        ifTaken.traceLocalVariableScope(scopeDiscoverer);
        if (elseBlock != null) {
            elseBlock.traceLocalVariableScope(scopeDiscoverer);
        }
    }

    @Override
    public boolean isRecursivelyStructured() {
        if (!ifTaken.isFullyStructured()) return false;
        if (elseBlock != null && !elseBlock.isFullyStructured()) return false;
        return true;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredIf)) return false;
        StructuredIf other = (StructuredIf) o;
        if (!conditionalExpression.equals(other.conditionalExpression)) return false;

        matchIterator.advance();
        return true;
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        conditionalExpression = expressionRewriter.rewriteExpression(conditionalExpression, null, this.getContainer(), null);
    }

    public StructuredStatement convertToAssertion(StructuredAssert structuredAssert) {
        if (elseBlock == null) {
            return structuredAssert;
        }
        /* For some reason, we've created an assert with an else block
         * (a different optimisation must have thought it made sense!)
         */
        LinkedList<Op04StructuredStatement> list = ListFactory.newLinkedList();
        list.add(new Op04StructuredStatement(structuredAssert));
        list.add(elseBlock);
        return new Block(list, false);
    }

    @Override
    public void removePointlessBlocks(StructuredScope scope) {
        if (elseBlock != null && elseBlock.getStatement().isEffectivelyNOP()) {
            elseBlock = null;
        }
    }
}
