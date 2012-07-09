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
public class StructuredSwitch extends AbstractStructuredStatement {
    private Expression switchOn;
    private Op04StructuredStatement body;
    private final BlockIdentifier blockIdentifier;

    public StructuredSwitch(Expression switchOn, Op04StructuredStatement body, BlockIdentifier blockIdentifier) {
        this.switchOn = switchOn;
        this.body = body;
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("switch (" + switchOn + ") ");
        body.dump(dumper);
    }

    @Override
    public boolean isProperlyStructured() {
        return true;
    }

}
