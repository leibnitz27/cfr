package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CompOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ComparisonOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.TernaryExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableFactory;

import java.util.List;

public class BadCompareRewriter extends AbstractExpressionRewriter {
    private final VariableFactory vf;

    BadCompareRewriter(VariableFactory vf) {
        this.vf = vf;
    }

    public void rewrite(List<Op03SimpleStatement> op03SimpleParseNodes) {
        for (Op03SimpleStatement stm : op03SimpleParseNodes) {
            stm.rewrite(this);
        }
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof ArithmeticOperation) {
            ArithmeticOperation operation = (ArithmeticOperation) expression;
            ArithOp op = operation.getOp();
            if (op.isTemporary()) {
                expression = rewriteTemporary(operation);
            }
        }
        return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
    }

    private Expression rewriteTemporary(ArithmeticOperation arith) {
        Expression lhs = arith.getLhs();
        Expression rhs = arith.getRhs();
        boolean safe = isSideEffectFree(lhs) && isSideEffectFree(rhs);
        ComparisonOperation compareEq;
        if (safe) {
            compareEq = new ComparisonOperation(lhs, rhs, CompOp.EQ);
        } else {
            LValue tmp = vf.tempVariable(lhs.getInferredJavaType());
            Expression zero = Literal.getLiteralOrNull(lhs.getInferredJavaType().getRawType(), lhs.getInferredJavaType(), 0);
            if (zero == null) {
                zero = Literal.INT_ZERO;
            }
            compareEq = new ComparisonOperation(new AssignmentExpression(tmp, new ArithmeticOperation(lhs, rhs, ArithOp.MINUS)), zero, CompOp.EQ);
            lhs = new LValueExpression(tmp);
            rhs = zero;
        }
        switch (arith.getOp()) {
            case LCMP:
                // CmpG will return 1 if either value is nan.
            case DCMPG:
            case FCMPG:
                return new TernaryExpression(compareEq, Literal.INT_ZERO,
                        new TernaryExpression(new ComparisonOperation(lhs, rhs, CompOp.LT), Literal.MINUS_ONE, Literal.INT_ONE));
            // CmpL will return -1 if either value is nan.
            case DCMPL:
            case FCMPL:
                return new TernaryExpression(compareEq, Literal.INT_ZERO,
                        new TernaryExpression(new ComparisonOperation(lhs, rhs, CompOp.GT), Literal.INT_ONE, Literal.MINUS_ONE));
        }
        return arith;
    }

    private boolean isSideEffectFree(Expression lhs) {
        if (!(lhs instanceof LValueExpression)) return false;
        LValue lv = ((LValueExpression) lhs).getLValue();
        // don't even trust field variables, in case we've snuck a side effect into the object.
        return (lv instanceof LocalVariable);
    }
}
