package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.Optional;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class StructuredCatch extends AbstractStructuredStatement {
    private final List<JavaRefTypeInstance> catchTypes;
    private final Op04StructuredStatement catchBlock;
    private final LValue catching;
    private final Set<BlockIdentifier> possibleTryBlocks;

    public StructuredCatch(Collection<JavaRefTypeInstance> catchTypes, Op04StructuredStatement catchBlock, LValue catching, Set<BlockIdentifier> possibleTryBlocks) {
        this.catchTypes = catchTypes == null ? null : ListFactory.newList(catchTypes);
        this.catchBlock = catchBlock;
        this.catching = catching;
        this.possibleTryBlocks = possibleTryBlocks;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(catchTypes);
        if (!collector.isStatementRecursive()) return;
        catchBlock.collectTypeUsages(collector);
    }

    @Override
    public Dumper dump(Dumper dumper) {
        boolean first = true;
        dumper.print("catch (");
        for (JavaRefTypeInstance catchType : catchTypes) {
            if (!first) dumper.print(" | ");
            dumper.dump(catchType);
            first = false;
        }
        dumper.print(" ").dump(catching).print(") ");
        catchBlock.dump(dumper);
        return dumper;
    }


    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    @Override
    public boolean fallsNopToNext() {
        return true;
    }

    @Override
    public boolean isScopeBlock() {
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
        Optional<Op04StructuredStatement> maybeStatement = block.getMaybeJustOneStatement();
        if (!maybeStatement.isSet()) return false;
        StructuredStatement inBlock = maybeStatement.getValue().getStatement();
        StructuredThrow test = new StructuredThrow(new LValueExpression(catching));
        return (test.equals(inBlock));
    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        if (catching instanceof LocalVariable) {
            scopeDiscoverer.collectLocalVariableAssignment((LocalVariable) catching, this.getContainer(), null);
        }
        scopeDiscoverer.processOp04Statement(catchBlock);
    }

    @Override
    public List<LValue> findCreatedHere() {
        return ListFactory.newImmutableList(catching);
    }

    @Override
    public void markCreator(LValue scopedEntity, StatementContainer<StructuredStatement> hint) {
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
    }

    public Set<BlockIdentifier> getPossibleTryBlocks() {
        return possibleTryBlocks;
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
