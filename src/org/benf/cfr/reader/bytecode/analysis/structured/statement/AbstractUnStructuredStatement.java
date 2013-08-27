package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.util.Predicate;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public abstract class AbstractUnStructuredStatement extends AbstractStructuredStatement {

    @Override
    public final void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
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
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
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

    @Override
    public boolean isEffectivelyNOP() {
        return false;
    }

    @Override
    public List<LocalVariable> findCreatedHere() {
        return null;
    }

    @Override
    public String suggestName(LocalVariable createdHere, Predicate<String> testNameUsedFn) {
        return null;
    }
}
