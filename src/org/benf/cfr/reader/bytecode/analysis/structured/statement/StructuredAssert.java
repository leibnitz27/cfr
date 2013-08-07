package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredAssert extends AbstractStructuredStatement {

    ConditionalExpression conditionalExpression;

    public StructuredAssert(ConditionalExpression conditionalExpression) {
        this.conditionalExpression = conditionalExpression;
    }


    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("assert (").dump(conditionalExpression).print(")").endCodeln();
    }

    @Override
    public StructuredStatement informBlockHeirachy(Vector<BlockIdentifier> blockIdentifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        conditionalExpression.collectUsedLValues(scopeDiscoverer);
    }

    @Override
    public boolean isRecursivelyStructured() {
        return true;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredAssert)) return false;
        StructuredAssert other = (StructuredAssert) o;
        if (!conditionalExpression.equals(other.conditionalExpression)) return false;

        matchIterator.advance();
        return true;
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        conditionalExpression = expressionRewriter.rewriteExpression(conditionalExpression, null, this.getContainer(), null);
    }

}
