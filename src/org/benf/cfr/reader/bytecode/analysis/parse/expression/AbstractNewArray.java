package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

public abstract class AbstractNewArray extends AbstractExpression {
    AbstractNewArray(InferredJavaType inferredJavaType) {
        super(inferredJavaType);
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    public abstract int getNumDims();

    public abstract int getNumSizedDims();

    public abstract Expression getDimSize(int dim);
}
