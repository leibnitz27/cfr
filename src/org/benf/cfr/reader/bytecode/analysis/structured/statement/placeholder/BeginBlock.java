package org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;

public class BeginBlock extends AbstractPlaceholder {
    private final Block block;

    public BeginBlock(Block block) {
        this.block = block;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement current = matchIterator.getCurrent();
        if (current instanceof BeginBlock) {
            BeginBlock other = (BeginBlock) current;
            if (block == null || block.equals(other.block)) {
                matchIterator.advance();
                return true;
            }
        }
        return false;
    }
}
