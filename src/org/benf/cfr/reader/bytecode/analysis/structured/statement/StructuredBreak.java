package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredBreak extends AbstractStructuredStatement {

    private final BlockIdentifier breakBlock;
    private final boolean localBreak;

    public StructuredBreak(BlockIdentifier breakBlock, boolean localBreak) {
        this.breakBlock = breakBlock;
        this.localBreak = localBreak;
    }

    @Override
    public void dump(Dumper dumper) {
        if (localBreak) {
            dumper.print("break;\n");
        } else {
            dumper.print("break " + breakBlock.getName() + ";\n");
        }
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }

}
