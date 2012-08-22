package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredExpressionStatement extends AbstractStructuredStatement {
    private Expression expression;

    public StructuredExpressionStatement(Expression expression) {
        this.expression = expression;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print(expression.toString() + "\n");
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }
}
