package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
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
public class ArithmeticMutationOperation extends AbstractMutatingAssignmentExpression {
    private LValue mutated;
    private final ArithOp op;
    private Expression mutation;

    public ArithmeticMutationOperation(LValue mutated, Expression mutation, ArithOp op) {
        super(mutated.getInferredJavaType());
        this.mutated = mutated;
        this.op = op;
        this.mutation = mutation;
    }

    @Override
    public String toString() {
        return "" + mutated + "" + op.getShowAs() + "=" + mutation.toString();
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        mutation = mutation.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        // TODO : Rvalue?
        mutation = expressionRewriter.rewriteExpression(mutation, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public boolean isSelfMutatingOp1(LValue lValue, ArithOp arithOp) {
        return this.mutated.equals(lValue) &&
                this.op == arithOp &&
                this.mutation.equals(new Literal(TypedLiteral.getInt(1)));
    }

    @Override
    public ArithmeticPostMutationOperation getPostMutation() {
        return new ArithmeticPostMutationOperation(mutated, op);
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        mutation.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ArithmeticMutationOperation)) return false;

        ArithmeticMutationOperation other = (ArithmeticMutationOperation) o;

        return mutated.equals(other.mutated) &&
                op.equals(other.op) &&
                mutation.equals(other.mutation);
    }
}
