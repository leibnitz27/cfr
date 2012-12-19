package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:51
 * To change this template use File | Settings | File Templates.
 */
public class ArithmeticOperation extends AbstractExpression {
    private Expression lhs;
    private Expression rhs;
    private final ArithOp op;

    public ArithmeticOperation(Expression lhs, Expression rhs, ArithOp op) {
        super(inferredType(lhs.getInferredJavaType(), rhs.getInferredJavaType()));
        this.lhs = lhs;
        this.rhs = rhs;
        this.op = op;
    }

    private static InferredJavaType inferredType(InferredJavaType a, InferredJavaType b) {
        // TODO : verify same
        return new InferredJavaType(a.getRawType(), InferredJavaType.Source.OPERATION);
    }

    @Override
    public String toStringWithOuterPrecedence(int outerPrecedence) {
        int precedence = op.getPrecedence();
        boolean braceNeeded = true;
        if (outerPrecedence >= 0 && precedence >= 0) {
            if (outerPrecedence <= precedence) braceNeeded = false;
        }
        return (braceNeeded ? "(" : "") + lhs.toStringWithOuterPrecedence(precedence) + " " + op.getShowAs() + " " + rhs.toStringWithOuterPrecedence(precedence) + (braceNeeded ? ")" : "");
    }

    @Override
    public String toString() {
        return toStringWithOuterPrecedence(-1);
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

    public boolean isMutationOf(LValue lValue) {
        return ((lhs instanceof LValueExpression) && isLValueExprFor((LValueExpression) lhs, lValue) && !op.isTemporary());
    }

    public AbstractMutatingAssignmentExpression getMutationOf(LValue lValue) {
        if (!isMutationOf(lValue)) {
            throw new ConfusedCFRException("Can't get a mutation where none exists");
        }
        if (rhs.equals(new Literal(TypedLiteral.getInt(1)))) {
            return new ArithmeticPreMutationOperation(lValue, op);
        } else {
            return new ArithmeticMutationOperation(lValue, rhs, op);
        }
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        lhs = lhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        rhs = rhs.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        lhs = expressionRewriter.rewriteExpression(lhs, ssaIdentifiers, statementContainer, flags);
        rhs = expressionRewriter.rewriteExpression(rhs, ssaIdentifiers, statementContainer, flags);
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
        return op == other.op &&
                lhs.equals(other.lhs) &&
                rhs.equals(other.rhs);
    }

    private static CompOp rewriteXCMPCompOp(CompOp from, int on) {
        if (on == 0) return from;
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
        /*
         * TODO: Not quite behaving correctly here wrt floating point, i.e. DCMPG vs DCMPL.
         */
        switch (op) {
            case DCMPG:
            case FCMPG:
            case DCMPL:
            case FCMPL:
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
        return new ComparisonOperation(this.lhs, this.rhs, compOp);
    }
}
