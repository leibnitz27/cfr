package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.util.output.Dumper;

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
}
