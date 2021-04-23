package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Map;

import static org.benf.cfr.reader.bytecode.analysis.parse.utils.ReadWrite.READ;

/*
 * Wraps a local, a static or an instance field.
 */
public class LValueExpression extends AbstractExpression {
    private LValue lValue;

    public LValueExpression(LValue lValue) {
        super(BytecodeLoc.NONE, lValue.getInferredJavaType());
        this.lValue = lValue;
    }

    public LValueExpression(BytecodeLoc loc, LValue lValue) {
        super(loc, lValue.getInferredJavaType());
        this.lValue = lValue;
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return getLoc();
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new LValueExpression(getLoc(), cloneHelper.replaceOrClone(lValue));
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        lValue.collectTypeUsages(collector);
    }

    @Override
    public boolean isSimple() {

        //
        // TODO : THIS IS A HACK.
        //
        // A field expression is 'simple' only if it's final.
        // The type check here is a complete hack - we allow
        // a = (b=c) to transform to
        // b = c;
        // a = c; (though we should chain)
        // But we can't do the same for objects as it messes up syncbloks.
        //return !(getInferredJavaType().getJavaTypeInstance().isComplexType());
        //return true;
        return false;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        if (lValueRewriter.explicitlyReplaceThisLValue(lValue)) {
            Expression replacement = lValueRewriter.getLValueReplacement(lValue, ssaIdentifiers, statementContainer);
            if (replacement != null) return replacement;
        }

        lValue = lValue.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        // An LValueExpression wraps an LValue to be used on the RHS.
        lValue = expressionRewriter.rewriteExpression(lValue, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        return lValue.dumpWithOuterPrecedence(d, getPrecedence(), Troolean.NEITHER);
    }

    public LValue getLValue() {
        return lValue;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        lValueUsageCollector.collect(lValue, READ);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof LValueExpression)) return false;
        return lValue.equals(((LValueExpression) o).getLValue());
    }

    @Override
    public int hashCode() {
        return lValue.hashCode();
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return false;
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        LValueExpression other = (LValueExpression) o;
        if (!constraint.equivalent(lValue, other.lValue)) return false;
        return true;
    }

    @Override
    public Literal getComputedLiteral(Map<LValue, Literal> display) {
        return display.get(lValue);
    }

    public static Expression of(LValue lValue) {
        if (lValue instanceof StackSSALabel) {
            return new StackValue(BytecodeLoc.NONE, (StackSSALabel)lValue);
        }
        return new LValueExpression(lValue);
    }
}
