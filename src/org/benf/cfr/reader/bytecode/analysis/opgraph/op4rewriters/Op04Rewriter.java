package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;

public interface Op04Rewriter {
    void rewrite(Op04StructuredStatement root);
}
