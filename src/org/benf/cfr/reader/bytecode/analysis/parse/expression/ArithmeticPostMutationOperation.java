package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * <p/>
 * (eg) x >>= 3,  ++x
 */
public class ArithmeticPostMutationOperation extends AbstractAssignmentExpression {
    private LValue mutated;
    private final ArithOp op;

    public ArithmeticPostMutationOperation(LValue mutated, ArithOp op) {
        super(mutated.getInferredJavaType());
        this.mutated = mutated;
        this.op = op;
    }

    @Override
    public String toString() {
        return "" + mutated + ((op == ArithOp.PLUS) ? "++" : "--");
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
    public ArithmeticPostMutationOperation getPostMutation() {
        throw new IllegalStateException();
    }

    @Override
    public boolean isSelfMutatingOp1(LValue lValue, ArithOp arithOp) {
        return this.mutated.equals(lValue) &&
                this.op == arithOp;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ArithmeticPostMutationOperation)) return false;

        ArithmeticPostMutationOperation other = (ArithmeticPostMutationOperation) o;

        return mutated.equals(other.mutated) &&
                op.equals(other.op);
    }
}
