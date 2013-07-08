package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredReturn extends AbstractStructuredStatement {

    /*
     * Note that this will be null if we're returning void.
     * If we're ACTUALLY returning null, this will be a null-expr.
     */
    private Expression value;

    public StructuredReturn() {
        this.value = null;
    }

    public StructuredReturn(Expression value) {
        this.value = value;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (value == null) {
            dumper.print("return;\n");
        } else {
            dumper.print("return ").dump(value).print(";\n");
        }
        return dumper;
    }

    public Expression getValue() {
        return value;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        if (value != null) value.collectUsedLValues(scopeDiscoverer);
    }


    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        if (value != null) {
            value = expressionRewriter.rewriteExpression(value, null, this.getContainer(), null);
        }
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredReturn)) return false;
        StructuredReturn other = (StructuredReturn) o;
        if (!value.equals(other.value)) return false;

        matchIterator.advance();
        return true;
    }
}
