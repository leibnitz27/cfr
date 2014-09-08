package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

public abstract class AbstractFunctionInvokation extends AbstractExpression {
    protected AbstractFunctionInvokation(InferredJavaType inferredJavaType) {
        super(inferredJavaType);
    }
}
