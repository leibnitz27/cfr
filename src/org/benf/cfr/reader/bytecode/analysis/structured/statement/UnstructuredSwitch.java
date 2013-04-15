package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
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
public class UnstructuredSwitch extends AbstractUnStructuredStatement {
    private Expression switchOn;
    private final BlockIdentifier blockIdentifier;

    public UnstructuredSwitch(Expression switchOn, BlockIdentifier blockIdentifier) {
        this.switchOn = switchOn;
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** switch (" + switchOn + ")\n");
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
                last.replaceContainedStatement(caseStatement.getEmptyStructuredCase());
            }
        }
        return new StructuredSwitch(switchOn, innerBlock, blockIdentifier);
    }
}
