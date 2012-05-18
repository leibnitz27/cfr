package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredIf extends AbstractStructuredStatement {
    private ConditionalExpression conditionalExpression;

    public UnstructuredIf(ConditionalExpression conditionalExpression) {
        this.conditionalExpression = conditionalExpression;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** if (" + conditionalExpression + ") goto " + getContainer().getTargetLabel(1) + "\n");
    }

    @Override
    public boolean isProperlyStructured() {
        return false;
    }

}
