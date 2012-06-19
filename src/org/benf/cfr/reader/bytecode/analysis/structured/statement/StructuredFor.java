package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Assignment;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredFor extends AbstractStructuredStatement {
    private ConditionalExpression condition;
    private Op04StructuredStatement body;
    private Assignment assignment;
    private final BlockIdentifier block;

    public StructuredFor(ConditionalExpression condition, Assignment assignment, Op04StructuredStatement body, BlockIdentifier block) {
        this.condition = condition;
        this.body = body;
        this.assignment = assignment;
        this.block = block;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("for (;" + condition.toString() + "; " + assignment + ") ");
        body.dump(dumper);
    }
}
