package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredReturn extends AbstractStructuredStatement {

    /*
     * Note that this will be null if we're returning void.
     * If we're ACTUALLY returning null, this will be a null-expr.
     */
    private Expression value;

    public StructuredReturn() {
        this.value = null;
    }

    public StructuredReturn(Expression value) {
        this.value = value;
    }

    @Override
    public void dump(Dumper dumper) {
        if (value == null) {
            dumper.print("return;\n");
        } else {
            dumper.print("return " + value + ";\n");
        }
    }

    public Expression getValue() {
        return value;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }
}
