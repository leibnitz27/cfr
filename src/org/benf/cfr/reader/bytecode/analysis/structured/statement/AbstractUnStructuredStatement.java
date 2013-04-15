package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public abstract class AbstractUnStructuredStatement extends AbstractStructuredStatement {

    @Override
    public final void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }

    @Override
    public final boolean isProperlyStructured() {
        return false;
    }

    @Override
    public final boolean isRecursivelyStructured() {
        return false;
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        throw new UnsupportedOperationException("Can't linarise an unstructured statement");
    }

    /*
     * We can't handle this.
     *
     * If the block hasn't been converted properly, op4 processing shouldn't be proceeding.
     */
    @Override
    public void traceLocalVariableScope(LValueAssignmentScopeDiscoverer scopeDiscoverer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        throw new UnsupportedOperationException();
    }


}
