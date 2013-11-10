package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.LinkedList;
import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredDo extends AbstractUnStructuredStatement {
    private BlockIdentifier blockIdentifier;

    public UnstructuredDo(BlockIdentifier blockIdentifier) {
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("** do \n");
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn) {
        if (blockIdentifier != this.blockIdentifier) {
            throw new RuntimeException("Do statement claiming wrong block");
        }
        UnstructuredWhile lastEndWhile = innerBlock.removeLastEndWhile();
        if (lastEndWhile != null) {
            ConditionalExpression condition = lastEndWhile.getCondition();
            return new StructuredDo(condition, innerBlock, blockIdentifier);
        }

        /*
         * If there were any ways of legitimately hitting the exit, we need a break.  If not, we don't.
         * do always points to while so it's not orphaned, so we're checking for > 1 parent.
         *
         * need to transform
         * do {
         * } ???
         *    ->
         * do {
         *  ...
         *  break;
         * } while (true);
         */
        /*
         * Can't do this
         * TODO : Fails controlflowtest7
         */
//        if (this.getContainer().getSources().size() > 1) {
        StructuredStatement inner = innerBlock.getStatement();
        if (!(inner instanceof Block)) {
            LinkedList<Op04StructuredStatement> blockContent = ListFactory.newLinkedList();
            blockContent.add(new Op04StructuredStatement(inner));
            inner = new Block(blockContent, true);
            innerBlock.replaceContainedStatement(inner);
        }
        Block block = (Block) inner;
        block.getBlockStatements().add(new Op04StructuredStatement(new StructuredBreak(blockIdentifier, true)));
//        }
        return new StructuredDo(null, innerBlock, blockIdentifier);
    }


}
