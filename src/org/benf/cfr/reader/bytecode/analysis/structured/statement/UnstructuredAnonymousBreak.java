package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Set;
import java.util.Vector;

public class UnstructuredAnonymousBreak extends AbstractUnStructuredStatement {

    private final BlockIdentifier blockEnding;

    public UnstructuredAnonymousBreak(BlockIdentifier blockEnding) {
        this.blockEnding = blockEnding;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("** break ").print(blockEnding.getName()).print("\n");
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
    }

    @Override
    public StructuredStatement informBlockHeirachy(Vector<BlockIdentifier> blockIdentifiers) {
        return null;
    }

    public StructuredStatement tryExplicitlyPlaceInBlock(BlockIdentifier block) {
        if (block != blockEnding) {
            return this;
        }
        block.addForeignRef();
        return new StructuredBreak(block, false);
    }
}
