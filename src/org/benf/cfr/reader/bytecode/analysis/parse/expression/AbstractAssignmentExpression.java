package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

public abstract class AbstractAssignmentExpression extends AbstractExpression {

    public AbstractAssignmentExpression(InferredJavaType inferredJavaType) {
        super(inferredJavaType);
    }

    public abstract boolean isSelfMutatingOp1(LValue lValue, ArithOp arithOp);

    public abstract ArithmeticPostMutationOperation getPostMutation();

    public abstract LValue getUpdatedLValue();
}
