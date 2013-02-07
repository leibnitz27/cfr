package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;


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

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredAssignment)) return false;
        StructuredAssignment other = (StructuredAssignment) o;
        if (!lvalue.equals(other.lvalue)) return false;
        if (!rvalue.equals(other.rvalue)) return false;
        matchIterator.advance();
        return true;
    }
}

