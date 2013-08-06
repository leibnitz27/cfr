package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2012
 */
/*
 * Wraps a local, a static or an instance field.
 */
public class LValueExpression extends AbstractExpression {
    private LValue lValue;

    public LValueExpression(LValue lValue) {
        super(lValue.getInferredJavaType());
        this.lValue = lValue;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new LValueExpression(cloneHelper.replaceOrClone(lValue));
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
    public Dumper dump(Dumper d) {
        return lValue.dump(d);
    }

    public LValue getLValue() {
        return lValue;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        lValueUsageCollector.collect(lValue);
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
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        LValueExpression other = (LValueExpression) o;
        if (!constraint.equivalent(lValue, other.lValue)) return false;
        return true;
    }

}
