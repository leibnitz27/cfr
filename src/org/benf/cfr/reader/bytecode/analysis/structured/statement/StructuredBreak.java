package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Triplet;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Set;
import java.util.Stack;

public class StructuredBreak extends AbstractStructuredStatement {

    private final BlockIdentifier breakBlock;
    private final boolean localBreak;

    public StructuredBreak(BlockIdentifier breakBlock, boolean localBreak) {
        this.breakBlock = breakBlock;
        this.localBreak = localBreak;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (localBreak) {
            dumper.print("break;\n");
        } else {
            dumper.print("break " + breakBlock.getName() + ";\n");
        }
        return dumper;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
    }

    public BlockIdentifier getBreakBlock() {
        return breakBlock;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredBreak)) return false;
        StructuredBreak other = (StructuredBreak) o;
        if (!breakBlock.equals(other.breakBlock)) return false;
        // Don't check locality.
        matchIterator.advance();
        return true;
    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
    }

    public StructuredBreak maybeTightenToLocal(Stack<Triplet<StructuredStatement, BlockIdentifier, Set<Op04StructuredStatement>>> scopes) {
        if (localBreak) return this;
        /*
         * ok, it's not local.  Go up the scopes, and find the enclosing block, then see if the innermost breakable also
         * falls through to the same target.  If so, we can convert it to a local break.
         */
        Triplet<StructuredStatement, BlockIdentifier, Set<Op04StructuredStatement>> local = scopes.peek();
        if (local.getSecond() == breakBlock) {
            // well this is wrong.  Should be marked as a local break!
            return this;
        }
        for (int i = scopes.size() - 2; i >= 0; i--) {
            Triplet<StructuredStatement, BlockIdentifier, Set<Op04StructuredStatement>> scope = scopes.get(i);
            if (scope.getSecond() == breakBlock) {
                // Ok, this is the actual block we're breaking out of.
                Set<Op04StructuredStatement> localNext = local.getThird();
                Set<Op04StructuredStatement> actualNext = scope.getThird();
                if (localNext.containsAll(actualNext)) {
                    breakBlock.releaseForeignRef();
                    return new StructuredBreak(local.getSecond(), true);
                } else {
                    return this;
                }
            }
        }
        return this;
    }
}
