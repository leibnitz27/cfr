package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.KnownJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/07/2012
 * Time: 06:40
 */
public abstract class AbstractExpression implements Expression {
    private KnownJavaType knownJavaType;

    public AbstractExpression(KnownJavaType knownJavaType) {
        this.knownJavaType = knownJavaType;
    }

    @Override
    public boolean canPushDownInto() {
        return false;
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public Expression pushDown(Expression toPush, Expression parent) {
        throw new ConfusedCFRException("Push down not supported.");
    }

    @Override
    public KnownJavaType knownType() {
        return knownJavaType;
    }
}
