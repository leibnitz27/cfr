package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.output.Dumper;

public class CastExpression extends AbstractExpression implements BoxingProcessor {
    private Expression child;
    private boolean forced;

    public CastExpression(InferredJavaType knownType, Expression child) {
        super(knownType);
        InferredJavaType childInferredJavaType = child.getInferredJavaType();
        if (knownType.getJavaTypeInstance() == RawJavaType.LONG &&
            childInferredJavaType.getJavaTypeInstance() == RawJavaType.BOOLEAN) {
            childInferredJavaType.forceType(RawJavaType.INT, true);
        }
        RawJavaType knownTypeRawType = knownType.getRawType();
        if (childInferredJavaType.getRawType() == RawJavaType.BOOLEAN && knownTypeRawType != RawJavaType.BOOLEAN && knownTypeRawType.getStackType() == StackType.INT) {
            Expression tmp = new TernaryExpression(new BooleanExpression(child), Literal.INT_ONE, Literal.INT_ZERO);
            child = tmp;
        }
        this.child = child;
        this.forced = false;
    }

    public CastExpression(InferredJavaType knownType, Expression child, boolean forced) {
        super(knownType);
        this.child = child;
        this.forced = forced;
    }

    public boolean isForced() {
        return forced;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new CastExpression(getInferredJavaType(), cloneHelper.replaceOrClone(child), forced);
    }

    public boolean couldBeImplicit(GenericTypeBinder gtb) {
        if (forced) return false;
        JavaTypeInstance childType = child.getInferredJavaType().getJavaTypeInstance();
        JavaTypeInstance tgtType = getInferredJavaType().getJavaTypeInstance();
        return childType.implicitlyCastsTo(tgtType, gtb);
    }

    public boolean couldBeImplicit(JavaTypeInstance tgtType, GenericTypeBinder gtb) {
        if (forced) return false;
        JavaTypeInstance childType = child.getInferredJavaType().getJavaTypeInstance();
        return childType.implicitlyCastsTo(tgtType, gtb);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(getInferredJavaType().getJavaTypeInstance());
        child.collectTypeUsages(collector);
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.UNARY_OTHER;
    }

    /*
     * Actions in here make this slightly gross.  Would be nicer to convert ahead of time.
     */
    @Override
    public Dumper dumpInner(Dumper d) {
        JavaTypeInstance castType = getInferredJavaType().getJavaTypeInstance();
        while (castType instanceof JavaWildcardTypeInstance) {
            castType = ((JavaWildcardTypeInstance) castType).getUnderlyingType();
        }
        if (castType.getInnerClassHereInfo().isAnonymousClass()) {
            d.dump(child);
            return d;
        }
        if (castType == RawJavaType.NULL) {
            child.dumpWithOuterPrecedence(d, getPrecedence(), Troolean.NEITHER);
        } else {
            d.print("(").dump(castType).print(")");
            child.dumpWithOuterPrecedence(d, getPrecedence(), Troolean.NEITHER);
        }
        return d;
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
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return applyExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
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
        if (isForced()) {
            return false;
        }
        while (child instanceof CastExpression) {
            CastExpression childCast = (CastExpression) child;
            JavaTypeInstance thisType = getInferredJavaType().getJavaTypeInstance();
            JavaTypeInstance childType = childCast.getInferredJavaType().getJavaTypeInstance();
            Expression grandChild = childCast.child;
            JavaTypeInstance grandChildType = grandChild.getInferredJavaType().getJavaTypeInstance();
//            if (thisType.implicitlyCastsTo(grandChildType)) {
//                child = childCast.child;
//            } else {
//                break;
//            }
            if (Literal.NULL.equals(grandChild) && !thisType.isObject() && childType.isObject()) {
                break;
            }
            if (grandChildType.implicitlyCastsTo(childType, null) && childType.implicitlyCastsTo(thisType, null)) {
                child = childCast.child;
            } else {
                if (grandChildType instanceof RawJavaType && childType instanceof RawJavaType && thisType instanceof RawJavaType) {
                    if (!grandChildType.implicitlyCastsTo(childType, null) && !childType.implicitlyCastsTo(thisType, null)) {
                        child = childCast.child;
                        continue;
                    }
                }
                break;
            }
        }
        Expression newchild = boxingRewriter.sugarNonParameterBoxing(child, getInferredJavaType().getJavaTypeInstance());
        if (child != newchild &&
            newchild.getInferredJavaType().getJavaTypeInstance().implicitlyCastsTo(child.getInferredJavaType().getJavaTypeInstance(), null)) {
            child = newchild;
        }
        return false;
    }

    @Override
    public void applyNonArgExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
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
        while (e instanceof CastExpression && ((CastExpression) e).couldBeImplicit(null)) {
            e = ((CastExpression) e).getChild();
        }
        return e;
    }

    public static Expression removeImplicitOuterType(Expression e, GenericTypeBinder gtb, boolean rawArg) {
        final JavaTypeInstance t = e.getInferredJavaType().getJavaTypeInstance();
        while (e instanceof CastExpression
                && ((CastExpression) e).couldBeImplicit(gtb)
                && ((CastExpression) e).couldBeImplicit(t, gtb)) {
            Expression newE = ((CastExpression) e).getChild();
            if (!rawArg) {
                boolean wasRaw = e.getInferredJavaType().getJavaTypeInstance() instanceof RawJavaType;
                boolean isRaw = newE.getInferredJavaType().getJavaTypeInstance() instanceof RawJavaType;
                if (wasRaw && wasRaw != isRaw) {
                    break;
                }
            }
            e = newE;
        }
        return e;
    }

}
