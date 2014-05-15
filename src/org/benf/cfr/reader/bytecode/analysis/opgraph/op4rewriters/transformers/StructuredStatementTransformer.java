package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

public interface StructuredStatementTransformer {
    StructuredStatement transform(StructuredStatement in, StructuredScope scope);
}
