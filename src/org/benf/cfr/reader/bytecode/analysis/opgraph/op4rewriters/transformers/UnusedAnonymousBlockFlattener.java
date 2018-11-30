package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;

public class UnusedAnonymousBlockFlattener implements StructuredStatementTransformer {
    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        if (in instanceof Block) {
            ((Block)in).flattenOthersIn();
        }
        in.transformStructuredChildren(this, scope);
        return in;
    }
}
