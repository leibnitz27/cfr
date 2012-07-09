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
public class StructuredCase extends AbstractStructuredStatement {
    private Expression value;
    private Op04StructuredStatement body;
    private final BlockIdentifier blockIdentifier;

    public StructuredCase(Expression value, Op04StructuredStatement body, BlockIdentifier blockIdentifier) {
        this.value = value;
        this.body = body;
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public void dump(Dumper dumper) {
        if (value == null) {
            dumper.print("default: ");
        } else {
            dumper.print("case " + value + ": ");
        }
        body.dump(dumper);
    }

    @Override
    public boolean isProperlyStructured() {
        return true;
    }

}
