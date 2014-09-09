package org.benf.cfr.reader.bytecode.analysis.opgraph.op2rewriters;

import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

public class TypeHintRecoveryNone implements TypeHintRecovery {
    public static final TypeHintRecoveryNone INSTANCE = new TypeHintRecoveryNone();

    private TypeHintRecoveryNone() {
    }

    @Override
    public void improve(InferredJavaType type) {}
}
