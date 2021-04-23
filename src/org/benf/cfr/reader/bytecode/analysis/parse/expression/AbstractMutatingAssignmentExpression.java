package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;

public abstract class AbstractMutatingAssignmentExpression extends AbstractAssignmentExpression {
    AbstractMutatingAssignmentExpression(BytecodeLoc loc, InferredJavaType inferredJavaType) {
        super(loc, inferredJavaType);
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return !(getInferredJavaType().getJavaTypeInstance() instanceof RawJavaType);
    }

    @Override
    public boolean isValidStatement() {
        return true;
    }
}
