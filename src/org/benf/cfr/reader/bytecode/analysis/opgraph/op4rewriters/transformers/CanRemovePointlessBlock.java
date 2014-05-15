package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;

public interface CanRemovePointlessBlock {
    void removePointlessBlocks(StructuredScope scope);
}
