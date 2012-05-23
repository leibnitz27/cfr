package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredTry extends AbstractStructuredStatement {
    private final ExceptionGroup exceptionGroup;
    private Op04StructuredStatement tryBlock;

    public StructuredTry(ExceptionGroup exceptionGroup, Op04StructuredStatement tryBlock) {
        this.exceptionGroup = exceptionGroup;
        this.tryBlock = tryBlock;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("try /* " + exceptionGroup + "*/ ");
        tryBlock.dump(dumper);
    }

    @Override
    public boolean isProperlyStructured() {
        return true;
    }

}
