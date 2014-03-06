package org.benf.cfr.reader.bytecode.analysis.parse.lvalue;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 20/07/2012
 * Time: 05:50
 */
public abstract class AbstractLValue implements LValue {
    private InferredJavaType inferredJavaType;

    public AbstractLValue(InferredJavaType inferredJavaType) {
        this.inferredJavaType = inferredJavaType;
    }

    protected String typeToString() {
        return inferredJavaType.toString();
    }

    @Override
    public InferredJavaType getInferredJavaType() {
        return inferredJavaType;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(inferredJavaType.getJavaTypeInstance());
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
    }

    @Override
    public LValue outerDeepClone(CloneHelper cloneHelper) {
        return cloneHelper.replaceOrClone(this);
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return caught.mightCatchUnchecked();
    }

    @Override
    public final String toString() {
        return ToStringDumper.toString(this);
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
