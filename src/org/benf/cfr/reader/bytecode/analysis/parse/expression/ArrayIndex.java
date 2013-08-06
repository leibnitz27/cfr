package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:44
 * To change this template use File | Settings | File Templates.
 */
public class ArrayIndex extends AbstractExpression implements BoxingProcessor {
    private Expression array;
    private Expression index;

    public ArrayIndex(Expression array, Expression index) {
        super(new InferredJavaType(array.getInferredJavaType().getJavaTypeInstance().removeAnArrayIndirection(), InferredJavaType.Source.OPERATION));
        this.array = array;
        this.index = index;
        index.getInferredJavaType().useAsWithoutCasting(RawJavaType.INT);
    }

    private ArrayIndex(InferredJavaType inferredJavaType, Expression array, Expression index) {
        super(inferredJavaType);
        this.array = array;
        this.index = index;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ArrayIndex(getInferredJavaType(), cloneHelper.replaceOrClone(array), cloneHelper.replaceOrClone(index));
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.dump(array).print("[").dump(index).print("]");
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        array = array.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        index = index.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        array = expressionRewriter.rewriteExpression(array, ssaIdentifiers, statementContainer, flags);
        index = expressionRewriter.rewriteExpression(index, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        array.collectUsedLValues(lValueUsageCollector);
        index.collectUsedLValues(lValueUsageCollector);
    }

    public Expression getArray() {
        return array;
    }

    public Expression getIndex() {
        return index;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ArrayIndex)) return false;
        ArrayIndex other = (ArrayIndex) o;
        return array.equals(other.array) &&
                index.equals(other.index);
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == this) return true;
        if (!(o instanceof ArrayIndex)) return false;
        ArrayIndex other = (ArrayIndex) o;
        if (!constraint.equivalent(array, other.array)) return false;
        if (!constraint.equivalent(index, other.index)) return false;
        return true;
    }

    @Override
    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        index = boxingRewriter.sugarUnboxing(index);
        return false;
    }
}
