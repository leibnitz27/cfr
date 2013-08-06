package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredFinally extends AbstractStructuredStatement {
    private final Op04StructuredStatement catchBlock;

    public StructuredFinally(Op04StructuredStatement catchBlock) {
        this.catchBlock = catchBlock;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("finally ");
        catchBlock.dump(dumper);
        return dumper;
    }


    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, Op04StructuredStatement after) {
        catchBlock.transform(transformer, after);
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        catchBlock.linearizeStatementsInto(out);
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredFinally)) return false;
        StructuredFinally other = (StructuredFinally) o;
        // we don't actually check any equality for a match.
        matchIterator.advance();
        return true;
    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
    }


    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
    }
}
