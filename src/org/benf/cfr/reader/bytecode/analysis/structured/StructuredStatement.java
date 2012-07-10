package org.benf.cfr.reader.bytecode.analysis.structured;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.util.output.Dumpable;

import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 14/05/2012
 */
public interface StructuredStatement extends Dumpable {
    public void setContainer(Op04StructuredStatement container);

    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn);

    public StructuredStatement informBlockHeirachy(Vector<BlockIdentifier> blockIdentifiers);

    public boolean isProperlyStructured();
}
