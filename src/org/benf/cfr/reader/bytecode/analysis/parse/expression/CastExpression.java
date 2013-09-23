package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
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
public class CastExpression extends AbstractExpression implements BoxingProcessor {
    private Expression child;

    public CastExpression(InferredJavaType knownType, Expression child) {
        super(knownType);
        this.child = child;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new CastExpression(getInferredJavaType(), cloneHelper.replaceOrClone(child));
    }

    public boolean couldBeImplicit() {
        JavaTypeInstance childType = child.getInferredJavaType().getJavaTypeInstance();
        JavaTypeInstance tgtType = getInferredJavaType().getJavaTypeInstance();
        return childType.implicitlyCastsTo(tgtType);
    }

    public boolean couldBeImplicit(JavaTypeInstance tgtType) {
        JavaTypeInstance childType = child.getInferredJavaType().getJavaTypeInstance();
        return childType.implicitlyCastsTo(tgtType);
    }

    @Override
    public Dumper dump(Dumper d) {
        if (child.getInferredJavaType().getJavaTypeInstance() == RawJavaType.BOOLEAN &&
                !(RawJavaType.BOOLEAN.implicitlyCastsTo(getInferredJavaType().getJavaTypeInstance()))) {
            // This is ugly.  Unfortunately, it's necessary (currently!) as we don't have an extra pass to
            // transform invalid casts like this.
            return d.print("(" + getInferredJavaType().getCastString() + ")(").dump(child).print(" ? 1 : 0)");
        } else {
            return d.print("(" + getInferredJavaType().getCastString() + ")(").dump(child).print(")");
        }
    }


    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        child = child.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        child = expressionRewriter.rewriteExpression(child, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        child.collectUsedLValues(lValueUsageCollector);
    }

    public Expression getChild() {
        return child;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof CastExpression)) return false;
        return child.equals(((CastExpression) o).child);
    }

    @Override
    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        // Horrible edge case.  If we're forcibly downcasting a cast, then skip the middle one.
        while (child instanceof CastExpression) {
            CastExpression childCast = (CastExpression) child;
            JavaTypeInstance thisType = getInferredJavaType().getJavaTypeInstance();
            JavaTypeInstance childType = childCast.getInferredJavaType().getJavaTypeInstance();
            JavaTypeInstance grandChildType = childCast.child.getInferredJavaType().getJavaTypeInstance();
//            if (thisType.implicitlyCastsTo(grandChildType)) {
//                child = childCast.child;
//            } else {
//                break;
//            }
            if (grandChildType.implicitlyCastsTo(childType) && childType.implicitlyCastsTo(thisType)) {
                child = childCast.child;
            } else {
                if (grandChildType instanceof RawJavaType && childType instanceof RawJavaType && thisType instanceof RawJavaType) {
                    if (!grandChildType.implicitlyCastsTo(childType) && !childType.implicitlyCastsTo(thisType)) {
                        child = childCast.child;
                        continue;
                    }
                }
                break;
            }
        }
        Expression newchild = boxingRewriter.sugarNonParameterBoxing(child, getInferredJavaType().getJavaTypeInstance());
        if (newchild.getInferredJavaType().getJavaTypeInstance().implicitlyCastsTo(child.getInferredJavaType().getJavaTypeInstance())) {
            child = newchild;
        }
        return false;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        CastExpression other = (CastExpression) o;
        if (!constraint.equivalent(getInferredJavaType().getJavaTypeInstance(), other.getInferredJavaType().getJavaTypeInstance()))
            return false;
        if (!constraint.equivalent(child, other.child)) return false;
        return true;
    }

    public static Expression removeImplicit(Expression e) {
        while (e instanceof CastExpression && ((CastExpression) e).couldBeImplicit()) {
            e = ((CastExpression) e).getChild();
        }
        return e;
    }

    public static Expression removeImplicitOuterType(Expression e) {
        final JavaTypeInstance t = e.getInferredJavaType().getJavaTypeInstance();
        while (e instanceof CastExpression
                && ((CastExpression) e).couldBeImplicit()
                && ((CastExpression) e).couldBeImplicit(t)) {
            e = ((CastExpression) e).getChild();
        }
        return e;
    }

}
