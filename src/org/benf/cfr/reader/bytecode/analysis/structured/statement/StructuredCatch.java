package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredCatch extends AbstractStructuredStatement {
    private final String typeName;
    private final Op04StructuredStatement catchBlock;
    private final LValue catching;

    public StructuredCatch(String typeName, Op04StructuredStatement catchBlock, LValue catching) {
        this.typeName = typeName;
        this.catchBlock = catchBlock;
        this.catching = catching;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("catch (" + typeName + " " + catching + ") ");
        catchBlock.dump(dumper);
    }


    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
        catchBlock.transform(transformer);
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        catchBlock.linearizeStatementsInto(out);
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredCatch)) return false;
        StructuredCatch other = (StructuredCatch) o;
        // we don't actually check any equality for a match.
        matchIterator.advance();
        return true;
    }
}
