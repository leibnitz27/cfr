package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Vector;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredContinue extends AbstractStructuredContinue {

    private final BlockIdentifier continueTgt;

    public UnstructuredContinue(BlockIdentifier continueTgt) {
        this.continueTgt = continueTgt;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** continue;\n");
    }

    @Override
    public BlockIdentifier getContinueTgt() {
        return continueTgt;
    }

    @Override
    public StructuredStatement informBlockHeirachy(Vector<BlockIdentifier> blockIdentifiers) {

        boolean localBreak = false;
        BlockIdentifier outermostBreakable = BlockIdentifier.getInnermostBreakable(blockIdentifiers);
        if (outermostBreakable == continueTgt) {
            localBreak = true;
        } else {
            continueTgt.addForeignRef();
        }
        return new StructuredContinue(continueTgt, localBreak);
    }

    @Override
    public boolean isProperlyStructured() {
        return false;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }
}
