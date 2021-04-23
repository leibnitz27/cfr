package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Vector;

public class UnstructuredSwitch extends AbstractUnStructuredStatement {
    private Expression switchOn;
    private final BlockIdentifier blockIdentifier;
    private boolean safeExpression;

    public UnstructuredSwitch(BytecodeLoc loc, Expression switchOn, BlockIdentifier blockIdentifier, boolean safeExpression) {
        super(loc);
        this.switchOn = switchOn;
        this.blockIdentifier = blockIdentifier;
        this.safeExpression = safeExpression;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("** switch (").dump(switchOn).separator(")").newln();
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return BytecodeLoc.combine(this, switchOn);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collectFrom(switchOn);
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn) {
        if (blockIdentifier != this.blockIdentifier) {
            throw new ConfusedCFRException("Unstructured switch being asked to claim wrong block. [" + blockIdentifier + " != " + this.blockIdentifier + "]");
        }
        /*
         * If the last statement is an unstructured case, then we've got a case with no body.  Transform it into a structured
         * case.
         */
        if (innerBlock.getStatement() instanceof Block) {
            Block block = (Block) innerBlock.getStatement();
            List<Op04StructuredStatement> statements = block.getBlockStatements();
            Op04StructuredStatement last = statements.get(statements.size() - 1);
            if (last.getStatement() instanceof UnstructuredCase) {
                UnstructuredCase caseStatement = (UnstructuredCase) (last.getStatement());
                last.replaceStatement(caseStatement.getEmptyStructuredCase());
            }
        }
        return new StructuredSwitch(getLoc(), switchOn, innerBlock, blockIdentifier, safeExpression);
    }
}
