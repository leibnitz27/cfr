package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredCatch extends AbstractStructuredStatement {
    private final List<ExceptionGroup.Entry> exceptions;

    public UnstructuredCatch(List<ExceptionGroup.Entry> exceptions) {
        this.exceptions = exceptions;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("** catch " + exceptions + " { \n");
    }

    @Override
    public boolean isProperlyStructured() {
        return false;
    }

    @Override
    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier) {
        return null;
    }
}
