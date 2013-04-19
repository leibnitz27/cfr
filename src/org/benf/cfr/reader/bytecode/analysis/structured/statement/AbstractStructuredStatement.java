package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.StdOutDumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public abstract class AbstractStructuredStatement implements StructuredStatement {
    Op04StructuredStatement container;

    @Override
    public Op04StructuredStatement getContainer() {
        return container;
    }

    @Override
    public void setContainer(Op04StructuredStatement container) {
        this.container = container;
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn) {
        return null;
    }

    @Override
    public StructuredStatement informBlockHeirachy(Vector<BlockIdentifier> blockIdentifiers) {
        return null;
    }

    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    @Override
    public boolean isRecursivelyStructured() {
        return true;
    }

    /*
    * Unless we're an assignment or a block which could contain an assignment, there's no need to implement.
    */
    @Override
    public void traceLocalVariableScope(LValueAssignmentScopeDiscoverer scopeDiscoverer) {
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void markCreator(LocalVariable localVariable) {
        throw new IllegalArgumentException("Shouldn't be calling markCreator on " + this);
    }

    @Override
    public final String toString() {
        Dumper d = new ToStringDumper();
        d.print(getClass().toString()).dump(this);
        return d.toString();
    }
}
