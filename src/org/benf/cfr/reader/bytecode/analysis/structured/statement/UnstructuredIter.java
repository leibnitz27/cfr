package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Vector;

public class UnstructuredIter extends AbstractUnStructuredStatement {
    private BlockIdentifier blockIdentifier;
    private LValue iterator;
    private Expression list;

    public UnstructuredIter(BlockIdentifier blockIdentifier, LValue iterator, Expression list) {
        this.blockIdentifier = blockIdentifier;
        this.iterator = iterator;
        this.list = list;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("** for (").dump(iterator).print(" : ").dump(list).print(")\n");
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        iterator.collectTypeUsages(collector);
        collector.collectFrom(list);
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn) {
        if (blockIdentifier != this.blockIdentifier) {
            throw new RuntimeException("ForIter statement claiming wrong block");
        }
        innerBlock.removeLastContinue(blockIdentifier);
        return new StructuredIter(blockIdentifier, iterator, list, innerBlock);
    }


}
