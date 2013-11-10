package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.StdOutDumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/07/2012
 * Time: 06:40
 */
public abstract class AbstractExpression implements Expression {

    private final InferredJavaType inferredJavaType;

    public AbstractExpression(InferredJavaType inferredJavaType) {
        this.inferredJavaType = inferredJavaType;
    }

    /*
    protected String typeToString() {
        return inferredJavaType.toString();
    }
    */

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(inferredJavaType.getJavaTypeInstance());
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
    public InferredJavaType getInferredJavaType() {
        return inferredJavaType;
    }

    @Override
    public Dumper dumpWithOuterPrecedence(Dumper d, int outerPrecedence) {
        return dump(d);
    }

    @Override
    public Expression outerDeepClone(CloneHelper cloneHelper) {
        return cloneHelper.replaceOrClone(this);
    }

    @Override
    public final String toString() {
        return ToStringDumper.toString(this);
    }

    public abstract boolean equals(Object o);
}
