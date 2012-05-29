package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredContinue extends AbstractStructuredStatement {

    private final BlockIdentifier continueTgt;

    public StructuredContinue(BlockIdentifier continueTgt) {
        this.continueTgt = continueTgt;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("continue;\n");
    }

    public BlockIdentifier getContinueTgt() {
        return continueTgt;
    }
}
