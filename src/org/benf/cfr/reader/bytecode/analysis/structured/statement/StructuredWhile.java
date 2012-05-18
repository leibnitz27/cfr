package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredWhile extends AbstractStructuredStatement {
    private ConditionalExpression condition;
    private Op04StructuredStatement body;

    public StructuredWhile(ConditionalExpression condition, Op04StructuredStatement body) {
        this.condition = condition;
        this.body = body;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("while (" + condition.toString() + ") ");
        body.dump(dumper);
    }
}
