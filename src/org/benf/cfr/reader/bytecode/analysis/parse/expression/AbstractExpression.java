package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionVisitor;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

import java.util.Map;

public abstract class AbstractExpression implements Expression  {

    private final BytecodeLoc loc;
    private final InferredJavaType inferredJavaType;

    public AbstractExpression(BytecodeLoc loc, InferredJavaType inferredJavaType) {
        this.loc = loc;
        this.inferredJavaType = inferredJavaType;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(inferredJavaType.getJavaTypeInstance());
    }

    @Override
    public BytecodeLoc getLoc() {
        return loc;
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
    public boolean isValidStatement() {
        return false;
    }

    @Override
    public final Dumper dump(Dumper d) {
        return dumpWithOuterPrecedence(d, Precedence.WEAKEST, Troolean.NEITHER);
    }

    @Override
    public abstract Precedence getPrecedence();

    public abstract Dumper dumpInner(Dumper d);

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public final Dumper dumpWithOuterPrecedence(Dumper d, Precedence outerP, Troolean isLhs) {
        Precedence innerP = getPrecedence();
        int cmp = innerP.compareTo(outerP);
        boolean requires = false;
        if (cmp > 0) {
            requires = true;
        } else if (cmp == 0) {
            if (innerP == outerP && innerP.isCommutative()) {
                //noinspection ConstantConditions
                requires = false;
            } else {
                switch (isLhs) {
                    case TRUE:
                        // I.e. same precedence, we're on LHS, we only need braces if prec is R->L.
                        requires = !innerP.isLtoR();
                        break;
                    case FALSE:
                        requires = innerP.isLtoR();
                        break;
                    case NEITHER:
                        requires = !innerP.isLtoR();
                        break;
                }
            }
        }
        if (requires) {
            d.separator("(");
            dumpInner(d);
            d.separator(")");
        } else {
            dumpInner(d);
        }
        return d;
    }

}
