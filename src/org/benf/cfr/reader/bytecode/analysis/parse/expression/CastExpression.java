package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.LiteralFolding;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral.LiteralType;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.CastAction;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Map;

public class CastExpression extends AbstractExpression implements BoxingProcessor {
    private Expression child;
    private boolean forced;

    public CastExpression(BytecodeLoc loc, InferredJavaType knownType, Expression child) {
        super(loc, knownType);
        InferredJavaType childInferredJavaType = child.getInferredJavaType();
        if (knownType.getJavaTypeInstance() == RawJavaType.LONG &&
            childInferredJavaType.getJavaTypeInstance() == RawJavaType.BOOLEAN) {
            childInferredJavaType.forceType(RawJavaType.INT, true);
        }
        RawJavaType knownTypeRawType = knownType.getRawType();
        // It's ok to insert Boolean -> XX explicit casts at construction time, as we
        // don't have booleans early.
        if (childInferredJavaType.getRawType() == RawJavaType.BOOLEAN && knownTypeRawType != RawJavaType.BOOLEAN && knownTypeRawType.getStackType() == StackType.INT) {
            child = new TernaryExpression(loc, new BooleanExpression(child), Literal.INT_ONE, Literal.INT_ZERO);
        }
        this.child = child;
        this.forced = false;
    }

    public CastExpression(BytecodeLoc loc, InferredJavaType knownType, Expression child, boolean forced) {
        super(loc, knownType);
        this.child = child;
        this.forced = forced;
    }

    public boolean isForced() {
        return forced;
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return BytecodeLoc.combine(this, child);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new CastExpression(getLoc(), getInferredJavaType(), cloneHelper.replaceOrClone(child), forced);
    }

	@Override
	public Literal getComputedLiteral(Map<LValue, Literal> display) {
		if (!(getInferredJavaType().getJavaTypeInstance() instanceof RawJavaType))
			return null;
		Literal computedChild = child.getComputedLiteral(display);
		if (computedChild == null)
			return null;
		return LiteralFolding.foldCast(computedChild, (RawJavaType) getInferredJavaType().getJavaTypeInstance());
	}

    public boolean couldBeImplicit(GenericTypeBinder gtb) {
        if (forced) return false;
        JavaTypeInstance childType = child.getInferredJavaType().getJavaTypeInstance();
        JavaTypeInstance tgtType = getInferredJavaType().getJavaTypeInstance();
        return childType.implicitlyCastsTo(tgtType, gtb);
    }

    private boolean couldBeImplicit(JavaTypeInstance tgtType, GenericTypeBinder gtb) {
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
            d.separator("(").dump(castType).separator(")");
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
        CastExpression other = (CastExpression) o;
        if (!getInferredJavaType().getJavaTypeInstance().equals(other.getInferredJavaType().getJavaTypeInstance())) return false;
        return child.equals(other.child);
    }

    @Override
    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        // Horrible edge case.  If we're forcibly downcasting a cast, then skip the middle one.
        if (isForced()) {
            return false;
        }
        JavaTypeInstance thisType = getInferredJavaType().getJavaTypeInstance();
        while (child instanceof CastExpression) {
            CastExpression childCast = (CastExpression) child;
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
        Expression newchild = boxingRewriter.sugarNonParameterBoxing(child, thisType);
        JavaTypeInstance childType = child.getInferredJavaType().getJavaTypeInstance();
        JavaTypeInstance newChildType = newchild.getInferredJavaType().getJavaTypeInstance();
        if (child != newchild && newChildType.implicitlyCastsTo(childType, null)) {
            // We can do this, but only if the original cast wasn't deliberately lossy.
            if (childType.implicitlyCastsTo(thisType, null) ==
            newChildType.implicitlyCastsTo(thisType, null)) {
                child = newchild;
            }
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
                if (wasRaw && !isRaw) {
                    break;
                }
            }
            e = newE;
        }
        return e;
    }

    public static Expression tryRemoveCast(Expression e) {
        if (e instanceof CastExpression) {
            Expression ce = ((CastExpression) e).getChild();
            if (ce.getInferredJavaType().getJavaTypeInstance().implicitlyCastsTo(e.getInferredJavaType().getJavaTypeInstance(), null)) {
                e = ce;
            }
        }
        return e;
    }
}
