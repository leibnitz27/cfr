package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredCatch extends AbstractStructuredStatement {
    private final List<ExceptionGroup.Entry> exceptions;
    private final Op04StructuredStatement catchBlock;

    public StructuredCatch(List<ExceptionGroup.Entry> exceptions, Op04StructuredStatement catchBlock) {
        this.exceptions = exceptions;
        this.catchBlock = catchBlock;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("catch /* " + exceptions + "*/ ");
        catchBlock.dump(dumper);
    }


    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn) {
        return null;
    }
}
