package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Set;
import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredBreak extends AbstractUnStructuredStatement {

    private final Set<BlockIdentifier> blocksEnding;

    public UnstructuredBreak(Set<BlockIdentifier> blocksEnding) {
        this.blocksEnding = blocksEnding;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** break;\n");
    }

    @Override
    public StructuredStatement informBlockHeirachy(Vector<BlockIdentifier> blockIdentifiers) {
        /*
         * Find which of blocksEnding is outermost ( earliest ).
         */
        int index = Integer.MAX_VALUE;
        BlockIdentifier bestBlock = null;
        for (BlockIdentifier block : blocksEnding) {
            int posn = blockIdentifiers.indexOf(block);
            if (posn >= 0 && index > posn) {
                index = posn;
                bestBlock = block;
            }
        }
        if (bestBlock == null) {
            return null;
        }
        boolean localBreak = false;
        BlockIdentifier outermostBreakable = BlockIdentifier.getInnermostBreakable(blockIdentifiers);
        if (outermostBreakable == bestBlock) {
            localBreak = true;
        } else {
            bestBlock.addForeignRef();
        }
        return new StructuredBreak(bestBlock, localBreak);
    }
}
