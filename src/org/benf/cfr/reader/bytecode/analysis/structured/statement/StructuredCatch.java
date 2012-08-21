package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredCatch extends AbstractStructuredStatement {
    private final String typeName;
    private final Op04StructuredStatement catchBlock;

    public StructuredCatch(String typeName, Op04StructuredStatement catchBlock) {
        this.typeName = typeName;
        this.catchBlock = catchBlock;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("catch ( " + typeName + " ) ");
        catchBlock.dump(dumper);
    }


    @Override
    public boolean isProperlyStructured() {
        return true;
    }

}
