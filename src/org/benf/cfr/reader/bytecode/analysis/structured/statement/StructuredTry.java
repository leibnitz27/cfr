package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredTry extends AbstractStructuredStatement {
    private final ExceptionGroup exceptionGroup;
    private Op04StructuredStatement tryBlock;
    private List<Op04StructuredStatement> catchBlocks = ListFactory.newList();
    private Op04StructuredStatement finallyBlock;

    public StructuredTry(ExceptionGroup exceptionGroup, Op04StructuredStatement tryBlock) {
        this.exceptionGroup = exceptionGroup;
        this.tryBlock = tryBlock;
        this.finallyBlock = null;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("try ");
        tryBlock.dump(dumper);
        for (Op04StructuredStatement catchBlock : catchBlocks) {
            catchBlock.dump(dumper);
        }
        if (finallyBlock != null) {
            finallyBlock.dump(dumper);
        }
        return dumper;
    }

    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    public void addCatch(Op04StructuredStatement catchStatement) {
        catchBlocks.add(catchStatement);
    }

    public void addFinally(Op04StructuredStatement finallyBlock) {
        this.finallyBlock = finallyBlock;
    }

    public void removeFinalJumpsTo(Op04StructuredStatement after) {
        tryBlock.removeLastGoto(after);
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
        scope.add(this);
        try {
            tryBlock.transform(transformer, scope);
            for (Op04StructuredStatement catchBlock : catchBlocks) {
                catchBlock.transform(transformer, scope);
            }
            if (finallyBlock != null) {
                finallyBlock.transform(transformer, scope);
            }
        } finally {
            scope.remove(this);
        }
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        tryBlock.linearizeStatementsInto(out);
        for (Op04StructuredStatement catchBlock : catchBlocks) {
            catchBlock.linearizeStatementsInto(out);
        }
        if (finallyBlock != null) {
            finallyBlock.linearizeStatementsInto(out);
        }

    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        tryBlock.traceLocalVariableScope(scopeDiscoverer);
        for (Op04StructuredStatement catchBlock : catchBlocks) {
            catchBlock.traceLocalVariableScope(scopeDiscoverer);
        }
        if (finallyBlock != null) {
            finallyBlock.traceLocalVariableScope(scopeDiscoverer);
        }
    }

    @Override
    public boolean isRecursivelyStructured() {
        if (!tryBlock.isFullyStructured()) return false;
        for (Op04StructuredStatement catchBlock : catchBlocks) {
            if (!catchBlock.isFullyStructured()) return false;
        }
        if (finallyBlock != null) {
            if (!finallyBlock.isFullyStructured()) return false;
        }
        return true;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredTry)) return false;
        StructuredTry other = (StructuredTry) o;
        // we don't actually check any equality for a match.
        matchIterator.advance();
        return true;
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
    }


    @Override
    public boolean inlineable() {
        if (catchBlocks.isEmpty() && finallyBlock == null) {
            return true;
        }
        return false;
    }

    @Override
    public Op04StructuredStatement getInline() {
        return tryBlock;
    }
}
