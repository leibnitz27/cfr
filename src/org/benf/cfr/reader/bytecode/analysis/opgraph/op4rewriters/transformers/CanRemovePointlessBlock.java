package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/08/2013
 * Time: 11:53
 */
public interface CanRemovePointlessBlock {
    void removePointlessBlocks(StructuredScope scope);
}
