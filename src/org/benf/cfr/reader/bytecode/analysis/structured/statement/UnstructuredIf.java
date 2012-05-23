package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredIf extends AbstractStructuredStatement {
    private ConditionalExpression conditionalExpression;
    private Op04StructuredStatement setIfBlock;
    private BlockIdentifier knownIfBlock;
    private BlockIdentifier knownElseBlock;

    public UnstructuredIf(ConditionalExpression conditionalExpression, BlockIdentifier knownIfBlock, BlockIdentifier knownElseBlock) {
        this.conditionalExpression = conditionalExpression;
        this.knownIfBlock = knownIfBlock;
        this.knownElseBlock = knownElseBlock;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** if (" + conditionalExpression + ") goto " + getContainer().getTargetLabel(1) + "\n");
    }

    @Override
    public boolean isProperlyStructured() {
        return false;
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier) {
        if (blockIdentifier == knownIfBlock) {
            if (knownElseBlock == null) {
                return new StructuredIf(conditionalExpression.getNegatedExpression(), innerBlock);
            } else {
                setIfBlock = innerBlock;
                return this;
            }
        } else if (blockIdentifier == knownElseBlock) {
            if (setIfBlock == null) {
                throw new ConfusedCFRException("Set else block before setting IF block");
            }
            /* If this was a SIMPLE if, it ends in a jump to just after the ELSE block.
             * We need to snip that out.
             */
            if (knownIfBlock.getBlockType() == BlockType.SIMPLE_IF_TAKEN) {
                setIfBlock.removeLastGoto();
            }
            return new StructuredIf(conditionalExpression.getNegatedExpression(), setIfBlock, innerBlock);
        } else {
            return null;
//            throw new ConfusedCFRException("IF statement given blocks it doesn't recognise");
        }
    }
}
