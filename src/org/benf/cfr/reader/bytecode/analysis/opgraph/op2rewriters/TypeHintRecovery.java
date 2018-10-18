package org.benf.cfr.reader.bytecode.analysis.opgraph.op2rewriters;

import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

public interface TypeHintRecovery {
    void improve(InferredJavaType type);
}
