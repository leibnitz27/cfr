package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/07/2012
 * Time: 06:40
 */
public abstract class AbstractMutatingAssignmentExpression extends AbstractAssignmentExpression {

    public AbstractMutatingAssignmentExpression(InferredJavaType inferredJavaType) {
        super(inferredJavaType);
    }
}
