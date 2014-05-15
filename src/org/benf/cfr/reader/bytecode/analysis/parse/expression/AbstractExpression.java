package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

import java.util.Map;

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
    public Expression outerDeepClone(CloneHelper cloneHelper) {
        return cloneHelper.replaceOrClone(this);
    }

    @Override
    public final String toString() {
        return ToStringDumper.toString(this);
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return true;
    }

    public abstract boolean equals(Object o);

    @Override
    public Literal getComputedLiteral(Map<LValue, Literal> display) {
        return null;
    }

    @Override
    public final Dumper dump(Dumper d) {
        return dumpWithOuterPrecedence(d, Precedence.WEAKEST);
    }

    @Override
    public abstract Precedence getPrecedence();

    public abstract Dumper dumpInner(Dumper d);

    @Override
    public final Dumper dumpWithOuterPrecedence(Dumper d, Precedence outerP) {
        Precedence innerP = getPrecedence();
        int cmp = innerP.compareTo(outerP);
        if (cmp > 0 || cmp == 0 && !innerP.isLtoR()) {
            d.print("(");
            dumpInner(d);
            d.print(")");
        } else {
            dumpInner(d);
        }
        return d;
    }
}
