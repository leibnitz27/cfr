package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/07/2012
 * Time: 06:40
 */
public abstract class AbstractAssignmentExpression extends AbstractExpression {

    public AbstractAssignmentExpression(InferredJavaType inferredJavaType) {
        super(inferredJavaType);
    }

    public abstract boolean isSelfMutatingIncr1(LValue lValue);
}
