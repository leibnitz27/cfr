package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredWhile extends AbstractStructuredStatement {
    private ConditionalExpression condition;
    private BlockIdentifier blockIdentifier;

    public UnstructuredWhile(ConditionalExpression condition, BlockIdentifier blockIdentifier) {
        this.condition = condition;
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** while (" + condition.toString() + ")\n");
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier) {
        if (blockIdentifier != this.blockIdentifier) {
            throw new RuntimeException("While statement claiming wrong block");
        }
        innerBlock.removeLastContinue(blockIdentifier);
        return new StructuredWhile(condition, innerBlock, blockIdentifier);
    }

    @Override
    public boolean isProperlyStructured() {
        return false;
    }


}
