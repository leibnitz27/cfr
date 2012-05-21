package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredTry extends AbstractStructuredStatement {

    public UnstructuredTry() {
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** try " + getContainer().getTargetLabel(0) + " { \n");
    }

    @Override
    public boolean isProperlyStructured() {
        return false;
    }

}
