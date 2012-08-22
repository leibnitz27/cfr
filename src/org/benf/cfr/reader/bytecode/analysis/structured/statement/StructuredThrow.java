package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredThrow extends AbstractStructuredStatement {
    private Expression value;

    public StructuredThrow(Expression value) {
        this.value = value;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("throw " + value + ";\n");
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }
}
