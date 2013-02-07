package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredSynchronized extends AbstractStructuredStatement {
    private Expression monitor;
    private BlockIdentifier blockIdentifier;
    private Op04StructuredStatement body;

    public StructuredSynchronized(Expression monitor, BlockIdentifier blockIdentifier, Op04StructuredStatement body) {
        this.monitor = monitor;
        this.blockIdentifier = blockIdentifier;
        this.body = body;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("synchronized (" + monitor.toString() + ") ");
        body.dump(dumper);
    }

    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
        body.transform(transformer);
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        body.linearizeStatementsInto(out);
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        throw new UnsupportedOperationException();
    }

}
