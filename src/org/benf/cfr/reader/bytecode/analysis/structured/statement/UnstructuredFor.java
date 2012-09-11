package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AbstractAssignment;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredFor extends AbstractUnStructuredStatement {
    private ConditionalExpression condition;
    private BlockIdentifier blockIdentifier;
    private AssignmentSimple initial;
    private AbstractAssignment assignment;

    public UnstructuredFor(ConditionalExpression condition, BlockIdentifier blockIdentifier, AssignmentSimple initial, AbstractAssignment assignment) {
        this.condition = condition;
        this.blockIdentifier = blockIdentifier;
        this.initial = initial;
        this.assignment = assignment;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** for (" + initial + ";" + condition + "; " + assignment + ")\n");
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn) {
        if (blockIdentifier != this.blockIdentifier) {
            throw new RuntimeException("For statement claiming wrong block");
        }
        innerBlock.removeLastContinue(blockIdentifier);
        return new StructuredFor(condition, initial, assignment, innerBlock, blockIdentifier);
    }

}
