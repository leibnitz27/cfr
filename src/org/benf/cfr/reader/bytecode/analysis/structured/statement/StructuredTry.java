package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class StructuredTry extends AbstractStructuredStatement {
    private final ExceptionGroup exceptionGroup;
    private Op04StructuredStatement tryBlock;
    private List<Op04StructuredStatement> catchBlocks = ListFactory.newList();
    private Op04StructuredStatement finallyBlock;
    private final BlockIdentifier tryBlockIdentifier;
    private List<Op04StructuredStatement> resourceBlock;

    public StructuredTry(ExceptionGroup exceptionGroup, Op04StructuredStatement tryBlock, BlockIdentifier tryBlockIdentifier) {
        this.exceptionGroup = exceptionGroup;
        this.tryBlock = tryBlock;
        this.finallyBlock = null;
        this.tryBlockIdentifier = tryBlockIdentifier;
        this.resourceBlock = null;
    }

    public void addResources(List<Op04StructuredStatement> resources) {
        if (resourceBlock == null) resourceBlock = ListFactory.newList();
        resourceBlock.addAll(resources);
    }

    public List<Op04StructuredStatement> getResources() {
        return resourceBlock;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasResources() {
        return resourceBlock != null;
    }

    public List<Op04StructuredStatement> getCatchBlocks() {
        return catchBlocks;
    }

    public void clearCatchBlocks() {
        catchBlocks.clear();
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("try ");
        if (resourceBlock != null) {
            dumper.print("(");
            boolean first = true;
            for (Op04StructuredStatement resource : resourceBlock) {
                if (!first) dumper.print("     ");
                resource.dump(dumper);
                first = false;
            }
            dumper.removePendingCarriageReturn();
            dumper.print(")");
        }
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
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collectFrom(tryBlock);
        collector.collectFrom(catchBlocks);
        collector.collectFrom(finallyBlock);
        collector.collectFrom(resourceBlock);
    }

    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    @Override
    public boolean fallsNopToNext() {
        return true;
    }

    public void addCatch(Op04StructuredStatement catchStatement) {
        catchBlocks.add(catchStatement);
    }

    public void setFinally(Op04StructuredStatement finallyBlock) {
        this.finallyBlock = finallyBlock;
    }

    public void removeFinalJumpsTo(Op04StructuredStatement after) {
        tryBlock.removeLastGoto(after);
    }

    @Override
    public boolean isScopeBlock() {
        return true;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
        if (resourceBlock != null) {
            for (Op04StructuredStatement resource : resourceBlock) {
                resource.transform(transformer, scope);
            }
        }
        tryBlock.transform(transformer, scope);
        for (Op04StructuredStatement catchBlock : catchBlocks) {
            catchBlock.transform(transformer, scope);
        }
        if (finallyBlock != null) {
            finallyBlock.transform(transformer, scope);
        }
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        if (resourceBlock != null) {
            // TODO: Synthetic 'resource' markers?
            for (Op04StructuredStatement resource : resourceBlock) {
                out.add(resource.getStatement());
            }
        }
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
        if (resourceBlock != null) {
            scopeDiscoverer.enterBlock(this);
            for (Op04StructuredStatement resource : resourceBlock) {
                scopeDiscoverer.processOp04Statement(resource);
            }
        }
        scopeDiscoverer.processOp04Statement(tryBlock);
        for (Op04StructuredStatement catchBlock : catchBlocks) {
            scopeDiscoverer.processOp04Statement(catchBlock);
        }
        if (finallyBlock != null) {
            scopeDiscoverer.processOp04Statement(finallyBlock);
        }
        if (resourceBlock != null) {
            scopeDiscoverer.leaveBlock(this);
        }
    }

    @Override
    public boolean isRecursivelyStructured() {
        if (resourceBlock != null) {
            for (Op04StructuredStatement resource: resourceBlock) {
                if (!resource.isFullyStructured()) return false;
            }
        }
        if (!tryBlock.isFullyStructured()) return false;
        for (Op04StructuredStatement catchBlock : catchBlocks) {
            if (!catchBlock.isFullyStructured()) return false;
        }
        if (finallyBlock != null) {
            if (!finallyBlock.isFullyStructured()) return false;
        }
        return true;
    }

    public Op04StructuredStatement getFinallyBlock() {
        return finallyBlock;
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

    private boolean isPointlessTry() {
        if (!catchBlocks.isEmpty()) return false;
        if (finallyBlock == null) return true;
        // If finally block is empty, we can remove.
        if (!(finallyBlock.getStatement() instanceof StructuredFinally)) return false;
        StructuredFinally structuredFinally = (StructuredFinally) finallyBlock.getStatement();
        Op04StructuredStatement finallyCode = structuredFinally.getCatchBlock();
        if (!(finallyCode.getStatement() instanceof Block)) return false;
        Block block = (Block) finallyCode.getStatement();
        if (block.isEffectivelyNOP()) return true;
        return false;
    }

    private boolean isJustTryCatchThrow() {
        if (resourceBlock != null) return false;
        if (finallyBlock != null) return false;
        if (catchBlocks.size() != 1) return false;
        Op04StructuredStatement catchBlock = catchBlocks.get(0);
        StructuredStatement catchS = catchBlock.getStatement();
        if (!(catchS instanceof StructuredCatch)) return false;
        StructuredCatch structuredCatch = (StructuredCatch) catchS;
        return structuredCatch.isRethrow();
    }


    @Override
    public boolean inlineable() {
        // split out for breakpointing.
        if (isPointlessTry() || isJustTryCatchThrow()) {
            return true;
        } else {
            return false;
        }
    }

    public BlockIdentifier getTryBlockIdentifier() {
        return tryBlockIdentifier;
    }

    @Override
    public Op04StructuredStatement getInline() {
        return tryBlock;
    }

    public void setTryBlock(Op04StructuredStatement tryBlock) {
        this.tryBlock = tryBlock;
    }
}
