package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;


/**
 * Created:
 * User: lee
 * Date: 14/05/2012
 */
public class StructuredAssignment extends AbstractStructuredStatement {

    private LValue lvalue;
    private Expression rvalue;

    public StructuredAssignment(LValue lvalue, Expression rvalue) {
        this.lvalue = lvalue;
        this.rvalue = rvalue;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print(lvalue.toString() + " = " + rvalue.toString() + ";\n");
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }
}
