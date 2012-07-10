package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredContinue extends AbstractStructuredContinue {

    private final BlockIdentifier continueTgt;
    private final boolean localContinue;

    public StructuredContinue(BlockIdentifier continueTgt, boolean localContinue) {
        this.continueTgt = continueTgt;
        this.localContinue = localContinue;
    }

    @Override
    public void dump(Dumper dumper) {
        if (localContinue) {
            dumper.print("continue;\n");
        } else {
            dumper.print("continue " + continueTgt.getName() + ";\n");
        }
    }

    @Override
    public BlockIdentifier getContinueTgt() {
        return continueTgt;
    }
}
