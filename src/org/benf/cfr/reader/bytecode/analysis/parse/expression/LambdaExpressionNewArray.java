package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.Dumper;

/*
 * It would be nice to roll this into the standard lambda expressions, however new arrays
 * don't have a method reference, as this is a bytecode primitive.
 */
public class LambdaExpressionNewArray extends AbstractExpression implements LambdaExpressionCommon {
    private final InferredJavaType constrType;

    public LambdaExpressionNewArray(BytecodeLoc loc, InferredJavaType resType, InferredJavaType constrType) {
        super(loc, resType);
        this.constrType = constrType;
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return getLoc();
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        return d.dump(constrType.getJavaTypeInstance()).print("::").methodName(MiscConstants.NEW, null,true, false);
    }

    @Override
    public boolean childCastForced() {
        return false;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        return this.equals(o);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LambdaExpressionNewArray)) return false;
        LambdaExpressionNewArray other = (LambdaExpressionNewArray)o;
        return other.getInferredJavaType().getJavaTypeInstance().equals(getInferredJavaType().getJavaTypeInstance());
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.WEAKEST;
    }
}
