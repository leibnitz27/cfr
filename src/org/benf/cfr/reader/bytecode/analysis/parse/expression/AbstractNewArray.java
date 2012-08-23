package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:44
 */
public abstract class AbstractNewArray extends AbstractExpression {
    public AbstractNewArray(InferredJavaType inferredJavaType) {
        super(inferredJavaType);
    }

    public abstract int getNumDims();

    public abstract Expression getDimSize(int dim);

    public abstract JavaTypeInstance getInnerType();
}
