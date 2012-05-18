package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredBreak extends AbstractStructuredStatement {

    public StructuredBreak() {
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("break;\n");
    }
}
