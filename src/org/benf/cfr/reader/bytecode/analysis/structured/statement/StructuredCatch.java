package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredCatch extends AbstractStructuredStatement {
    private final JavaRefTypeInstance typeName;
    private final Op04StructuredStatement catchBlock;
    private final LValue catching;

    public StructuredCatch(JavaRefTypeInstance typeName, Op04StructuredStatement catchBlock, LValue catching) {
        this.typeName = typeName;
        this.catchBlock = catchBlock;
        this.catching = catching;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("catch (" + typeName + " ").dump(catching).print(") ");
        catchBlock.dump(dumper);
        return dumper;
    }


    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
        catchBlock.transform(transformer, scope);
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        catchBlock.linearizeStatementsInto(out);
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredCatch)) return false;
        StructuredCatch other = (StructuredCatch) o;
        // we don't actually check any equality for a match.
        matchIterator.advance();
        return true;
    }

    public boolean isRethrow() {
        StructuredStatement statement = catchBlock.getStatement();
        if (!(statement instanceof Block)) return false;
        Block block = (Block) statement;
        if (!block.isJustOneStatement()) return false;
        StructuredStatement inBlock = block.getSingleStatement().getStatement();
        StructuredThrow test = new StructuredThrow(new LValueExpression(catching));
        return (test.equals(inBlock));
    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
    }


    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
    }

    @Override
    public boolean isRecursivelyStructured() {
        return catchBlock.isFullyStructured();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StructuredCatch that = (StructuredCatch) o;

        if (catching != null ? !catching.equals(that.catching) : that.catching != null) return false;

        return true;
    }

}
