package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredTry;
import org.benf.cfr.reader.entities.ClassFile;

public abstract class TryResourceTransformerFinally extends TryResourcesTransformerBase{
    public TryResourceTransformerFinally(ClassFile classFile) {
        super(classFile);
    }

    @Override
    protected ResourceMatch getResourceMatch(StructuredTry structuredTry, StructuredScope scope) {
        Op04StructuredStatement finallyBlock = structuredTry.getFinallyBlock();
        return findResourceFinally(finallyBlock);
    }

    // If the finally block is
    // if (autoclosable != null) {
    //    close(exception, autoclosable)
    // }
    //
    // or
    //
    // close(exception, autoclosable)
    //
    // we can lift the autocloseable into the try.
    protected abstract ResourceMatch findResourceFinally(Op04StructuredStatement finallyBlock);

}
