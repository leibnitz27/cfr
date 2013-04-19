package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredCase extends AbstractUnStructuredStatement {
    private final List<Expression> values;
    private final BlockIdentifier blockIdentifier;

    public UnstructuredCase(List<Expression> values, BlockIdentifier blockIdentifier) {
        this.values = values;
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (values.isEmpty()) {
            dumper.print("** default:\n");
        } else {
            for (Expression value : values) {
                dumper.print("** case ").dump(value).print(":\n");
            }
        }
        return dumper;
    }

    public StructuredStatement getEmptyStructuredCase() {
        Op04StructuredStatement container = getContainer();
        return new StructuredCase(values,
                new Op04StructuredStatement(
                        container.getIndex().justAfter(),
                        container.getBlockMembership(),
                        Block.getEmptyBlock()),
                blockIdentifier);
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn) {
        if (blockIdentifier != this.blockIdentifier) {
            throw new ConfusedCFRException("Unstructured case being asked to claim wrong block. [" + blockIdentifier + " != " + this.blockIdentifier + "]");
        }
        return new StructuredCase(values, innerBlock, blockIdentifier);
    }
}
