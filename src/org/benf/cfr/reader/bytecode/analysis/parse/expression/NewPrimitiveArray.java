package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaArrayTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

public class NewPrimitiveArray extends AbstractNewArray {
    private Expression size;
    private final JavaTypeInstance type;

    public NewPrimitiveArray(BytecodeLoc loc, Expression size, byte type) {
        this(loc, size, ArrayType.getArrayType(type).getJavaTypeInstance());
    }

    public NewPrimitiveArray(BytecodeLoc loc, Expression size, JavaTypeInstance type) {
        // We don't really know anything about the array dimensionality, just the underlying type. :P
        super(loc, new InferredJavaType(new JavaArrayTypeInstance(1, type), InferredJavaType.Source.EXPRESSION));
        this.size = size;
        this.type = type;
        size.getInferredJavaType().useAsWithoutCasting(RawJavaType.INT);
    }

    private NewPrimitiveArray(BytecodeLoc loc, InferredJavaType inferredJavaType, JavaTypeInstance type, Expression size) {
        super(loc, inferredJavaType);
        this.type = type;
        this.size = size;
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return BytecodeLoc.combine(this, size);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        size.collectTypeUsages(collector);
        collector.collect(type);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new NewPrimitiveArray(getLoc(), getInferredJavaType(), type, cloneHelper.replaceOrClone(size));
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        return d.keyword("new ").print(type.toString()).separator("[").dump(size).separator("]");
    }

    @Override
    public int getNumDims() {
        return 1;
    }

    @Override
    public int getNumSizedDims() {
        return 1;
    }

    @Override
    public Expression getDimSize(int dim) {
        if (dim > 0) throw new ConfusedCFRException("Only 1 dimension for primitive arrays!");
        return size;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        size = size.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }


    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        size = expressionRewriter.rewriteExpression(size, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        size.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof NewPrimitiveArray)) return false;
        NewPrimitiveArray other = (NewPrimitiveArray) o;
        if (!size.equals(other.size)) return false;
        if (!type.equals(other.type)) return false;
        return true;
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        NewPrimitiveArray other = (NewPrimitiveArray) o;
        if (!constraint.equivalent(size, other.size)) return false;
        if (!constraint.equivalent(type, other.type)) return false;
        return true;
    }

}
