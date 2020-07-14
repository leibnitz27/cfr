package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

public abstract class AbstractAssignmentExpression extends AbstractExpression {

    public AbstractAssignmentExpression(BytecodeLoc loc, InferredJavaType inferredJavaType) {
        super(loc, inferredJavaType);
    }

    public abstract boolean isSelfMutatingOp1(LValue lValue, ArithOp arithOp);

    public abstract ArithmeticPostMutationOperation getPostMutation();

    public abstract ArithmeticPreMutationOperation getPreMutation();

    public abstract LValue getUpdatedLValue();
}
