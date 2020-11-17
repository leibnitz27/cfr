package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionVisitor;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.BasicExceptions;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Map;

public class ArithmeticOperation extends AbstractExpression implements BoxingProcessor {
    private Expression lhs;
    private Expression rhs;
    private final ArithOp op;

    public ArithmeticOperation(BytecodeLoc loc, Expression lhs, Expression rhs, ArithOp op) {
        super(loc, inferredType(lhs.getInferredJavaType(), rhs.getInferredJavaType(), op));
        this.lhs = lhs;
        this.rhs = rhs;
        this.op = op;
    }

    public ArithmeticOperation(BytecodeLoc loc, InferredJavaType knownType, Expression lhs, Expression rhs, ArithOp op) {
        super(loc, knownType);
        this.lhs = lhs;
        this.rhs = rhs;
        this.op = op;
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return BytecodeLoc.combine(this, lhs, rhs);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        lhs.collectTypeUsages(collector);
        rhs.collectTypeUsages(collector);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ArithmeticOperation(getLoc(), cloneHelper.replaceOrClone(lhs), cloneHelper.replaceOrClone(rhs), op);
    }

    private static InferredJavaType inferredType(InferredJavaType a, InferredJavaType b, ArithOp op) {
        InferredJavaType.useInArithOp(a, b, op);
        RawJavaType rawJavaType = a.getRawType();
        // We have to value convert.

        if (rawJavaType.getStackType().equals(StackType.INT)) {
            switch (op) {
                case AND:
                case OR:
                case XOR:
                    if (rawJavaType.equals(RawJavaType.BOOLEAN)) break;
                default:
                    rawJavaType = RawJavaType.INT;
                    break;
            }
        }
        return new InferredJavaType(rawJavaType, InferredJavaType.Source.OPERATION);
    }

    @Override
    public Precedence getPrecedence() {
        return op.getPrecedence();
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        lhs.dumpWithOuterPrecedence(d, getPrecedence(), Troolean.TRUE);
        d.operator(" " + op.getShowAs() + " ");
        rhs.dumpWithOuterPrecedence(d, getPrecedence(), Troolean.FALSE);
        return d;
    }

    @Override
    public Literal getComputedLiteral(Map<LValue, Literal> display) {
        Literal l = lhs.getComputedLiteral(display);
        if (l == null || !(l.getValue().getValue() instanceof Number))
            return null;
        Literal r = rhs.getComputedLiteral(display);
        if (r == null || !(r.getValue().getValue() instanceof Number))
            return null;
        // Since the values are numeric we know the lhs/rhs can be treated as raw types
        RawJavaType lType = (RawJavaType) l.getInferredJavaType().getJavaTypeInstance();
        RawJavaType rType = (RawJavaType) r.getInferredJavaType().getJavaTypeInstance();
        Number lv = ((Number) l.value.getValue());
        Number rv = ((Number) r.value.getValue());
        RawJavaType widestType = lType.ordinal() > rType.ordinal() ? lType : rType;
        switch (widestType) {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
                return getComputedInt(rv.intValue(), lv.intValue());
            case LONG:
                return getComputedLong(rv.longValue(), lv.longValue());
            case FLOAT:
                return getComputedFloat(rv.floatValue(), lv.floatValue());
            case DOUBLE:
                return getComputedDouble(rv.doubleValue(), lv.doubleValue());
        }
        return null;
    }

    private Literal getComputedInt(int rv, int lv) {
        int res;
        switch (op) {
            case PLUS:
                res = lv + rv;
                break;
            case MINUS:
                res = lv - rv;
                break;
            case MULTIPLY:
                res = lv * rv;
                break;
            case DIVIDE:
                if (rv == 0) return null;
                res = lv / rv;
                break;
            case REM:
                res = lv % rv;
                break;
            case OR:
                res = lv | rv;
                break;
            case AND:
                res = lv & rv;
                break;
            case SHR:
                res = lv >> rv;
                break;
            case SHL:
                res = lv << rv;
                break;
            case SHRU:
                res = lv >>> rv;
                break;
            case XOR:
                res = lv ^ rv;
                break;
            case NEG:
                res = ~lv;
                break;
            default:
                return null;
        }
        return new Literal(TypedLiteral.getInt(res));
    }

    private Literal getComputedLong(long rv, long lv) {
        long res;
        switch (op) {
            case PLUS:
                res = lv + rv;
                break;
            case MINUS:
                res = lv - rv;
                break;
            case MULTIPLY:
                res = lv * rv;
                break;
            case DIVIDE:
                if (rv == 0) return null;
                res = lv / rv;
                break;
            case REM:
                res = lv % rv;
                break;
            case OR:
                res = lv | rv;
                break;
            case AND:
                res = lv & rv;
                break;
            case SHR:
                res = lv >> rv;
                break;
            case SHL:
                res = lv << rv;
                break;
            case SHRU:
                res = lv >>> rv;
                break;
            case XOR:
                res = lv ^ rv;
                break;
            case NEG:
                res = ~lv;
                break;
            default:
                return null;
        }
        return new Literal(TypedLiteral.getLong(res));
    }

    private strictfp Literal getComputedFloat(float rv, float lv) {
        float res;
        switch (op) {
            case PLUS:
                res = lv + rv;
                break;
            case MINUS:
                res = lv - rv;
                break;
            case MULTIPLY:
                res = lv * rv;
                break;
            case DIVIDE:
                if (rv == 0) return null;
                res = lv / rv;
                break;
            case REM:
                res = lv % rv;
                break;
            default:
                return null;
        }
        return new Literal(TypedLiteral.getFloat(res));
    }

    private strictfp Literal getComputedDouble(double rv, double lv) {
        double res;
        switch (op) {
            case PLUS:
                res = lv + rv;
                break;
            case MINUS:
                res = lv - rv;
                break;
            case MULTIPLY:
                res = lv * rv;
                break;
            case DIVIDE:
                if (rv == 0) return null;
                res = lv / rv;
                break;
            case REM:
                res = lv % rv;
                break;
            default:
                return null;
        }
        return new Literal(TypedLiteral.getDouble(res));
    }

    private boolean isLValueExprFor(LValueExpression expression, LValue lValue) {
        LValue contained = expression.getLValue();
        return (lValue.equals(contained));
    }

    /*
     * Is this a very simple expression (a fn lit) / (lit fn a)?
     */
    public boolean isLiteralFunctionOf(LValue lValue) {
        if ((lhs instanceof LValueExpression) && (rhs instanceof Literal)) {
            return isLValueExprFor((LValueExpression) lhs, lValue);
        }
        if ((rhs instanceof LValueExpression) && (lhs instanceof Literal)) {
            return isLValueExprFor((LValueExpression) rhs, lValue);
        }
        return false;
    }

    public boolean isXorM1() {
        return (op == ArithOp.XOR && rhs.equals(Literal.MINUS_ONE));
    }

    public Expression getReplacementXorM1() {
        return new ArithmeticMonOperation(getLoc(), lhs, ArithOp.NEG);
    }

    public boolean isMutationOf(LValue lValue) {
        if (!(lhs instanceof LValueExpression)) return false;
        if (!isLValueExprFor((LValueExpression) lhs, lValue)) return false;
        if (op.isTemporary()) return false;
        return true;
    }

    public AbstractMutatingAssignmentExpression getMutationOf(LValue lValue) {
        if (!isMutationOf(lValue)) {
            throw new ConfusedCFRException("Can't get a mutation where none exists");
        }
        if (lhs.getInferredJavaType().getJavaTypeInstance() != RawJavaType.BOOLEAN &&
                Literal.equalsAnyOne(rhs)) {
            switch (op) {
                case PLUS:
                case MINUS:
                    return new ArithmeticPreMutationOperation(getLoc(), lValue, op);
            }
        }
        return new ArithmeticMutationOperation(getLoc(), lValue, rhs, op);
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        if (lValueRewriter.needLR()) {
            lhs = lhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
            rhs = rhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        } else {
            rhs = rhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
            lhs = lhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        lhs = expressionRewriter.rewriteExpression(lhs, ssaIdentifiers, statementContainer, flags);
        rhs = expressionRewriter.rewriteExpression(rhs, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        rhs = expressionRewriter.rewriteExpression(rhs, ssaIdentifiers, statementContainer, flags);
        lhs = expressionRewriter.rewriteExpression(lhs, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        lhs.collectUsedLValues(lValueUsageCollector);
        rhs.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public boolean canPushDownInto() {
        return op.isTemporary();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ArithmeticOperation)) return false;
        ArithmeticOperation other = (ArithmeticOperation) o;
        if (op != other.op) return false;
        if (!lhs.equals(other.lhs)) return false;
        if (!rhs.equals(other.rhs)) return false;
        return true;
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        ArithmeticOperation other = (ArithmeticOperation) o;
        if (op != other.op) return false;
        if (!constraint.equivalent(lhs, other.lhs)) return false;
        if (!constraint.equivalent(rhs, other.rhs)) return false;
        return true;
    }

    private static boolean returnsTrueForNaN(CompOp from, int on, boolean nanG) {
        if (on == 0) {
            if (nanG) {
                switch (from) {
                    case GTE:
                    case GT:
                        return true;
                }
            } else {
                switch (from) {
                    case LT:
                    case LTE:
                        return true;
                }
            }
        }
        return false;
    }

    private static boolean canNegateAroundNaN(CompOp from, int on) {
        if (on == 0) {
            switch (from) {
                case EQ:
                case NE:
                    return true;
            }
            return false;
        }
        return true;
    }

    private static CompOp rewriteXCMPCompOp(CompOp from, int on) {
        if (on == 0) {
            return from;
        }
        if (on < 0) {
            switch (from) {
                case LT:
                    throw new IllegalStateException("Bad CMP");
                case LTE:
                    return CompOp.LT;  // <= -1 -> < 0
                case GTE:
                    throw new IllegalStateException("Bad CMP");
                case GT:
                    return CompOp.GTE; // > -1 -> >= 0
                case EQ:
                    return CompOp.LT;  // == -1 -> < 0
                case NE:
                    return CompOp.GTE; // != -1 -> >= 0
                default:
                    throw new IllegalStateException("Unknown enum");
            }
        } else {
            switch (from) {
                case LT:
                    return CompOp.LTE; // < 1 -> <= 0
                case LTE:
                    throw new IllegalStateException("Bad CMP");
                case GTE:
                    return CompOp.GT; // >= 1 -> > 1
                case GT:
                    throw new IllegalStateException("Bad CMP");
                case EQ:
                    return CompOp.GT; // == 1 -> > 0
                case NE:
                    return CompOp.LTE; // != 1 -> <= 0
                default:
                    throw new IllegalStateException("Unknown enum");
            }
        }
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return lhs.canThrow(caught) ||
                rhs.canThrow(caught) ||
                op.canThrow(this.getInferredJavaType(), caught, BasicExceptions.instances);
    }

    @Override
    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        lhs = boxingRewriter.sugarUnboxing(lhs);
        rhs = boxingRewriter.sugarUnboxing(rhs);
//        if (boxingRewriter.isUnboxedType(lhs)) {
//            rhs = boxingRewriter.sugarUnboxing(rhs);
//            return false;
//        }
//        if (boxingRewriter.isUnboxedType(rhs)) {
//            lhs = boxingRewriter.sugarUnboxing(lhs);
//            return false;
//        }
        return false;
    }

    @Override
    public void applyNonArgExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
    }

    public Expression getLhs() {
        return lhs;
    }

    public Expression getRhs() {
        return rhs;
    }

    public ArithOp getOp() {
        return op;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /*
         * parent is (x LCMP y) > 0
         * (this is (x LCMP y)).
         */
    @Override
    public Expression pushDown(Expression toPush, Expression parent) {
        if (!(parent instanceof ComparisonOperation)) return null;
        if (!op.isTemporary()) return null;
        if (!(toPush instanceof Literal)) {
            throw new ConfusedCFRException("Pushing with a non-literal as pushee.");
        }
        ComparisonOperation comparisonOperation = (ComparisonOperation) parent;
        CompOp compOp = comparisonOperation.getOp();
        Literal literal = (Literal) toPush;
        TypedLiteral typedLiteral = literal.getValue();
        if (typedLiteral.getType() != TypedLiteral.LiteralType.Integer) {
            throw new ConfusedCFRException("<xCMP> , non integer!");
        }
        int litVal = (Integer) typedLiteral.getValue();
        switch (litVal) {
            case -1:
            case 0:
            case 1:
                break;
            default:
                throw new ConfusedCFRException("Invalid literal value " + litVal + " in xCMP");
        }
        /* https://docs.oracle.com/javase/specs/jvms/se6/html/Instructions2.doc3.html
         *
         * ...Otherwise, at least one of value1' or value2' is NaN. The dcmpg instruction pushes the int value 1
         * onto the operand stack and the dcmpl instruction pushes the int value -1 onto the operand stack.
         */
        boolean acceptsNaN = false;
        boolean canNegate = true;
        switch (op) {
            case DCMPG:
            case FCMPG:
                // litVal must be 0 here.  If it's not, we're confused!
                acceptsNaN = returnsTrueForNaN(compOp, litVal, true);
                canNegate = canNegateAroundNaN(compOp, litVal);
                break;
            case DCMPL:
            case FCMPL:
                // litVal must be 0 here.  If it's not, we're confused!
                acceptsNaN = returnsTrueForNaN(compOp, litVal, false);
                canNegate = canNegateAroundNaN(compOp, litVal);
                break;
            case LCMP:
                break;
            default:
                throw new ConfusedCFRException("Shouldn't be here.");
        }


        /* the structure is something like
         *
         *  (LHS <xCMP> RHS) compOp litVal
         *
         * Since there are only 3 possible returns (-1,0,1) from a xCMP, turn everything into OP 0.
         * i.e. = 1 --> > 0
         *      > -1 --> >= 0
         */
        compOp = rewriteXCMPCompOp(compOp, litVal);
        if (acceptsNaN) {
            ConditionalExpression comp = new ComparisonOperation(getLoc(), this.lhs, this.rhs, compOp.getInverted(), false);
            comp = new NotOperation(getLoc(), comp);
            return comp;
        }
        return new ComparisonOperation(getLoc(), this.lhs, this.rhs, compOp, canNegate);
    }
}
