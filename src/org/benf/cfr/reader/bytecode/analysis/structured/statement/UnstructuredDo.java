package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredDo extends AbstractStructuredStatement {
    private BlockIdentifier blockIdentifier;

    public UnstructuredDo(BlockIdentifier blockIdentifier) {
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** do \n");
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn) {
        if (blockIdentifier != this.blockIdentifier) {
            throw new RuntimeException("Do statement claiming wrong block");
        }
        ConditionalExpression condition = innerBlock.removeLastEndWhile().getCondition();
        return new StructuredDo(condition, innerBlock, blockIdentifier);
    }

    @Override
    public boolean isProperlyStructured() {
        return false;
    }


}
