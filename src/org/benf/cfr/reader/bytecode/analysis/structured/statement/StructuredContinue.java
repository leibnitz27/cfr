package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredContinue extends AbstractStructuredStatement {

    public StructuredContinue() {
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("continue;\n");
    }
}
