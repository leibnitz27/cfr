package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public abstract class AbstractUnStructuredStatement extends AbstractStructuredStatement {

    @Override
    public final void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }

    @Override
    public final boolean isProperlyStructured() {
        return false;
    }
}
