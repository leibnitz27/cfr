package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.LinkedList;
import java.util.Set;
import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredWhile extends AbstractUnStructuredStatement {
    private ConditionalExpression condition;
    private BlockIdentifier blockIdentifier;
    private Set<BlockIdentifier> blocksEndedAfter;

    public UnstructuredWhile(ConditionalExpression condition, BlockIdentifier blockIdentifier, Set<BlockIdentifier> blocksEndedAfter) {
        this.condition = condition;
        this.blockIdentifier = blockIdentifier;
        // We have to be careful here - if this while statement jumps out PAST an outer block when it
        // ends, we have to ADD a break to the correct block.
        this.blocksEndedAfter = blocksEndedAfter;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** while (" + condition.toString() + ")\n");
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn) {
        if (blockIdentifier != this.blockIdentifier) {
            throw new RuntimeException("While statement claiming wrong block");
        }
        innerBlock.removeLastContinue(blockIdentifier);
        /* If blocksEndedAfter includes blocks which are in 'blocksCurrentlyIn', then we're
         * breaking out of an outer block after this loop!
         */
        StructuredStatement whileLoop = new StructuredWhile(condition, innerBlock, blockIdentifier);

        BlockIdentifier externalBreak = BlockIdentifier.getOutermostEnding(blocksCurrentlyIn, blocksEndedAfter);
        if (externalBreak == null) {
            return whileLoop;
        }

        /* We have subsumed a break to an outer loop. :P */
        LinkedList<Op04StructuredStatement> lst = ListFactory.newLinkedList();
        lst.add(new Op04StructuredStatement(whileLoop));
        lst.add(new Op04StructuredStatement(new StructuredBreak(externalBreak, false)));
        return new Block(
                lst, false
        );
    }

    public ConditionalExpression getCondition() {
        return condition;
    }
}
