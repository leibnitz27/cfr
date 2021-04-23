package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.output.Dumper;

public class ArrayLength extends AbstractExpression {
    private Expression array;
    private JavaTypeInstance constructionType;

    public ArrayLength(BytecodeLoc loc, Expression array) {
        super(loc, new InferredJavaType(RawJavaType.INT, InferredJavaType.Source.INSTRUCTION));
        this.array = array;
        // We keep track of construction type, because if this array participates
        // in type clash resolution we'll lose it, which will lead to a bad cast.
        this.constructionType = array.getInferredJavaType().getJavaTypeInstance();
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return BytecodeLoc.combine(this, array);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ArrayLength(getLoc(), cloneHelper.replaceOrClone(array));
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        array.collectTypeUsages(collector);
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        // This is a bit of a hack - the alternative is to have a very late
        // stage transform, which I just dont think is worth the cost right now.
        Expression expr = array;
        if (expr.getInferredJavaType().getJavaTypeInstance().getNumArrayDimensions() == 0) {
            expr = new CastExpression(BytecodeLoc.NONE, new InferredJavaType(constructionType, InferredJavaType.Source.UNKNOWN), expr);
        }
        expr.dumpWithOuterPrecedence(d, getPrecedence(), Troolean.NEITHER);
        return d.print(".length");
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        array = array.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        array = expressionRewriter.rewriteExpression(array, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        array.collectUsedLValues(lValueUsageCollector);
    }

    public Expression getArray() {
        return array;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ArrayLength)) return false;
        return array.equals(((ArrayLength) o).getArray());
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        ArrayLength other = (ArrayLength) o;
        if (!constraint.equivalent(array, other.array)) return false;
        return true;
    }

}
