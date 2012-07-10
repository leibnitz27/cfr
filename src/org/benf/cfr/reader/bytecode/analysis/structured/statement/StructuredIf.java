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
public class StructuredIf extends AbstractStructuredStatement {

    ConditionalExpression conditionalExpression;
    Op04StructuredStatement ifTaken;
    Op04StructuredStatement elseBlock;

    public StructuredIf(ConditionalExpression conditionalExpression, Op04StructuredStatement ifTaken) {
        this(conditionalExpression, ifTaken, null);
    }

    public StructuredIf(ConditionalExpression conditionalExpression, Op04StructuredStatement ifTaken, Op04StructuredStatement elseBlock) {
        this.conditionalExpression = conditionalExpression;
        this.ifTaken = ifTaken;
        this.elseBlock = elseBlock;
    }


    @Override
    public void dump(Dumper dumper) {
        dumper.print("if (" + conditionalExpression + ") ");
        ifTaken.dump(dumper);
        if (elseBlock != null) {
            dumper.print("else ");
            elseBlock.dump(dumper);
        }
    }

    @Override
    public StructuredStatement informBlockHeirachy(Vector<BlockIdentifier> blockIdentifiers) {
        ifTaken.informBlockMembership(blockIdentifiers);
        if (elseBlock != null) elseBlock.informBlockMembership(blockIdentifiers);
        return null;
    }
}
