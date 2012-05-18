package org.benf.cfr.reader.bytecode.analysis.structured;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.util.output.Dumpable;

/**
 * Created:
 * User: lee
 * Date: 14/05/2012
 */
public interface StructuredStatement extends Dumpable {
    public void setContainer(Op04StructuredStatement container);

    public StructuredStatement claimBlock(Op04StructuredStatement innerBlock);

    public boolean isProperlyStructured();
}
