package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractMutatingAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithOp;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Set;

/**
 * In an assignment prechange, the LHS is by definition equal to the RHS after the statement.
 * I.e. x = ++x;
 * <p/>
 * y = y|=3;
 * <p/>
 * We can always drop the assignment, and just display this as the expression.
 * <p/>
 * As the name implies, this is not appropriate for postchanges, i.e. x++;
 * In order to do those, we will have a copy of the value before increment.  So we'll see
 * <p/>
 * i = x;
 * x = ++x; // (with our daft AssignmentMutation).
 * if (i ... )
 * <p/>
 * If we have a guaranteed single use of a pre-change, we can run it together with the PRIOR use, and convert
 * it to a post change.  Similarly, if we have a SINGLE use of a prechange AFTER, we can just move the prechange RHS.
 * <p/>
 * x = ++x;
 * if (x ) ......
 */
public class AssignmentPreMutation extends AbstractAssignment {
    private LValue lvalue;
    private AbstractAssignmentExpression rvalue;

    public AssignmentPreMutation(LValue lvalue, AbstractMutatingAssignmentExpression rvalue) {
        this.lvalue = lvalue;
        this.rvalue = rvalue;
        lvalue.getInferredJavaType().chain(rvalue.getInferredJavaType());
    }

    private AssignmentPreMutation(LValue lvalue, AbstractAssignmentExpression rvalue) {
        this.lvalue = lvalue;
        this.rvalue = rvalue;
    }

    @Override
    public Statement deepClone(CloneHelper cloneHelper) {
        return new AssignmentPreMutation(cloneHelper.replaceOrClone(lvalue), (AbstractAssignmentExpression)cloneHelper.replaceOrClone(rvalue));
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return rvalue.dump(dumper).endCodeln();
    }

    @Override
    public void collectLValueAssignments(LValueAssignmentCollector<Statement> lValueAssigmentCollector) {
        /*
         * Here, we override the default behaviour of the LValue being collected, and collect it anyway.
         * We will only want to allow a replacement if there is only a single usage of this value.
         */
        lValueAssigmentCollector.collectMutatedLValue(lvalue, this.getContainer(), rvalue);
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        lvalue.collectLValueUsage(lValueUsageCollector);
        rvalue.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public void collectObjectCreation(CreationCollector creationCollector) {
        creationCollector.collectCreation(lvalue, rvalue, this.getContainer());
    }

    @Override
    public SSAIdentifiers<LValue> collectLocallyMutatedVariables(SSAIdentifierFactory<LValue, ?> ssaIdentifierFactory) {
        return lvalue.collectVariableMutation(ssaIdentifierFactory);
    }

    @Override
    public LValue getCreatedLValue() {
        return lvalue;
    }

    @Override
    public Expression getRValue() {
        return rvalue;
    }

    @Override
    public boolean isSelfMutatingOperation() {
        return true;
    }


    @Override
    public boolean isSelfMutatingOp1(LValue lValue, ArithOp arithOp) {
        return rvalue.isSelfMutatingOp1(lValue, arithOp);
    }

    @Override
    public Expression getPostMutation() {
        return rvalue.getPostMutation();
    }

    @Override
    public Expression getPreMutation() {
        return rvalue.getPreMutation();
    }

    @Override
    public AbstractAssignmentExpression getInliningExpression() {
        return rvalue;
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        // If a premutation would have its RHS replaced with an rvalue that has a different version
        // of its own ssa identifier, this is invalid.
        // a += 2
        // b = a
        // a += b
        // IS NOT
        // a += (b = (a+=2))
        lvalue = lvalue.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
        Set fixed = getContainer().getSSAIdentifiers().getFixedHere();
        // anything in fixed CANNOT be assigned to inside rvalue.
        lValueRewriter = lValueRewriter.getWithFixed(fixed);
        rvalue = (AbstractAssignmentExpression) rvalue.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        lvalue = expressionRewriter.rewriteExpression(lvalue, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.LVALUE);
        rvalue = (AbstractAssignmentExpression) expressionRewriter.rewriteExpression(rvalue, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredExpressionStatement(rvalue, false);
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return rvalue.canThrow(caught);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AssignmentPreMutation)) return false;

        AssignmentPreMutation other = (AssignmentPreMutation) o;
        return lvalue.equals(other.lvalue) && rvalue.equals(other.rvalue);
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        AssignmentPreMutation other = (AssignmentPreMutation) o;
        if (!constraint.equivalent(lvalue, other.lvalue)) return false;
        if (!constraint.equivalent(rvalue, other.rvalue)) return false;
        return true;
    }
}
