package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.output.Dumper;

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
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ArithmeticMutationOperation(cloneHelper.replaceOrClone(mutated), cloneHelper.replaceOrClone(mutation), op);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        mutated.collectTypeUsages(collector);
        mutation.collectTypeUsages(collector);
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.ASSIGNMENT;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        d.dump(mutated).print(op.getShowAs() + "=");
        mutation.dumpWithOuterPrecedence(d, getPrecedence(), Troolean.NEITHER);
        return d;
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

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        ArithmeticMutationOperation other = (ArithmeticMutationOperation) o;
        if (!constraint.equivalent(op, other.op)) return false;
        if (!constraint.equivalent(mutated, other.mutated)) return false;
        if (!constraint.equivalent(mutation, other.mutation)) return false;
        return true;
    }

}
