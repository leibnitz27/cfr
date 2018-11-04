package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Vector;

public class UnstructuredCase extends AbstractUnStructuredStatement {
    private final List<Expression> values;
    private final BlockIdentifier blockIdentifier;
    private final InferredJavaType caseType;

    public UnstructuredCase(List<Expression> values, InferredJavaType caseType, BlockIdentifier blockIdentifier) {
        this.values = values;
        this.caseType = caseType;
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

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collectFrom(values);
        collector.collect(caseType.getJavaTypeInstance());
    }

    StructuredStatement getEmptyStructuredCase() {
        Op04StructuredStatement container = getContainer();
        return new StructuredCase(values, caseType,
                new Op04StructuredStatement(
                        container.getIndex().justAfter(),
                        container.getBlockMembership(),
                        Block.getEmptyBlock(false)),
                blockIdentifier);
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn) {
        if (blockIdentifier != this.blockIdentifier) {
            throw new ConfusedCFRException("Unstructured case being asked to claim wrong block. [" + blockIdentifier + " != " + this.blockIdentifier + "]");
        }
        return new StructuredCase(values, caseType, innerBlock, blockIdentifier);
    }
}
