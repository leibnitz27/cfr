package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;

public abstract class AbstractMutatingAssignmentExpression extends AbstractAssignmentExpression {

    AbstractMutatingAssignmentExpression(InferredJavaType inferredJavaType) {
        super(inferredJavaType);
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return !(getInferredJavaType().getJavaTypeInstance() instanceof RawJavaType);
    }

}
