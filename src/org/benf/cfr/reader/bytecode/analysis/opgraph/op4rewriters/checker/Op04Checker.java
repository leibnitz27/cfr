package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.checker;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.util.DecompilerComments;

public interface Op04Checker extends StructuredStatementTransformer {
    void commentInto(DecompilerComments comments);
}
