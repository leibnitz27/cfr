package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredGoto extends AbstractStructuredStatement {

    public UnstructuredGoto() {
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** GOTO " + getContainer().getTargetLabel(0) + "\n");
    }

    @Override
    public boolean isProperlyStructured() {
        return false;
    }

}
