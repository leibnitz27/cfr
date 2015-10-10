package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * 1d array only.
 */
public class NewAnonymousArray extends AbstractNewArray implements BoxingProcessor {
    private JavaTypeInstance allocatedType;
    private int numDims;
    private List<Expression> values;
    private boolean isCompletelyAnonymous = false;

    public NewAnonymousArray(InferredJavaType type, int numDims, List<Expression> values, boolean isCompletelyAnonymous) {
        super(type);
        this.values = ListFactory.newList();
        this.numDims = numDims;
        this.allocatedType = type.getJavaTypeInstance().getArrayStrippedType();
        if (allocatedType instanceof RawJavaType) {
            for (Expression value : values) {
                value.getInferredJavaType().useAsWithoutCasting((RawJavaType) allocatedType);
            }
        }
        for (Expression value : values) {
            if (value instanceof NewAnonymousArray) {
                NewAnonymousArray newAnonymousArrayInner = (NewAnonymousArray) value;
                newAnonymousArrayInner.isCompletelyAnonymous = true;
            }
            this.values.add(value);
        }
        this.isCompletelyAnonymous = isCompletelyAnonymous;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(allocatedType);
        collector.collectFrom(values);
    }

    @Override
    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        for (int i = 0; i < values.size(); ++i) {
            values.set(i, boxingRewriter.sugarNonParameterBoxing(values.get(i), allocatedType));
        }
        return false;
    }

    @Override
    public void applyNonArgExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new NewAnonymousArray(getInferredJavaType(), numDims, cloneHelper.replaceOrClone(values), isCompletelyAnonymous);
    }


    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        if (!isCompletelyAnonymous) {
            d.print("new ").dump(allocatedType);
            for (int x = 0; x < numDims; ++x) d.print("[]");
        }
        d.print("{");
        boolean first = true;
        for (Expression value : values) {
            first = StringUtils.comma(first, d);
            d.dump(value);
        }
        d.print("}");
        return d;
    }

    public List<Expression> getValues() {
        return values;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        for (int x = values.size()-1; x >= 0; --x) {
            values.set(x, values.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyForwards(values, expressionRewriter, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyBackwards(values, expressionRewriter, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        for (Expression value : values) {
            value.collectUsedLValues(lValueUsageCollector);
        }
    }


    @Override
    public int getNumDims() {
        return numDims;
    }

    @Override
    public int getNumSizedDims() {
        return 0;
    }

    @Override
    public Expression getDimSize(int dim) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JavaTypeInstance getInnerType() {
        return allocatedType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NewAnonymousArray that = (NewAnonymousArray) o;

        if (isCompletelyAnonymous != that.isCompletelyAnonymous) return false;
        if (numDims != that.numDims) return false;
        if (allocatedType != null ? !allocatedType.equals(that.allocatedType) : that.allocatedType != null)
            return false;
        if (values != null ? !values.equals(that.values) : that.values != null) return false;

        return true;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        NewAnonymousArray other = (NewAnonymousArray) o;

        if (isCompletelyAnonymous != other.isCompletelyAnonymous) return false;
        if (numDims != other.numDims) return false;
        if (!constraint.equivalent(allocatedType, other.allocatedType)) return false;
        if (!constraint.equivalent(values, other.values)) return false;
        return true;
    }
}
