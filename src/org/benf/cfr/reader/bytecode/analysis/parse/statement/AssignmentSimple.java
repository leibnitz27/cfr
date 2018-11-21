package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.output.Dumper;

public class AssignmentSimple extends AbstractAssignment {
    private LValue lvalue;
    private Expression rvalue;

    public AssignmentSimple(LValue lvalue, Expression rvalue) {
        this.lvalue = lvalue;
        this.rvalue = lvalue.getInferredJavaType().chain(rvalue.getInferredJavaType()).performCastAction(rvalue, lvalue.getInferredJavaType());
    }

    public AssignmentSimple(InferredJavaType type, LValue lvalue, Expression rvalue) {
        this.lvalue = lvalue;
        this.rvalue = rvalue;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.dump(lvalue).print(" = ").dump(rvalue).endCodeln();
    }

    @Override
    public void collectLValueAssignments(LValueAssignmentCollector<Statement> lValueAssigmentCollector) {
        lvalue.collectLValueAssignments(rvalue, this.getContainer(), lValueAssigmentCollector);
    }

    @Override
    public boolean doesBlackListLValueReplacement(LValue lValue, Expression expression) {
        return lvalue.doesBlackListLValueReplacement(lValue, expression);
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        rvalue.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public void collectObjectCreation(CreationCollector creationCollector) {
        creationCollector.collectCreation(lvalue, rvalue, this.getContainer());
    }

    @Override
    public SSAIdentifiers<LValue> collectLocallyMutatedVariables(SSAIdentifierFactory<LValue> ssaIdentifierFactory) {
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

    public void setRValue(Expression rvalue) {
        this.rvalue = rvalue;
    }

    /* We /should/ be using assignmentPreChange here, but if that has been disabled, these
         * assignments should be able to stand in.
         *
         * This (should) also catch self member calls?
         */
    @Override
    public boolean isSelfMutatingOperation() {
        Expression localR = rvalue;
        while (localR instanceof CastExpression) localR = ((CastExpression) localR).getChild();
        if (localR instanceof ArithmeticOperation) {
            ArithmeticOperation arithmeticOperation = (ArithmeticOperation) localR;
            if (arithmeticOperation.isLiteralFunctionOf(lvalue)) return true;
        } else if (localR instanceof MemberFunctionInvokation) {
            MemberFunctionInvokation memberFunctionInvokation = (MemberFunctionInvokation)localR;
            Expression object = memberFunctionInvokation.getObject();
            if (object instanceof LValueExpression) {
                LValue memberLValue = ((LValueExpression) object).getLValue();
                if (memberLValue.equals(lvalue)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean isSelfMutatingOp1(LValue lValue, ArithOp arithOp) {
        return false;
/*        if (!lValue.equals(this.lvalue)) return false;
        WildcardMatch wildcardMatch = new WildcardMatch();

        return wildcardMatch.match(
                new ArithmeticOperation(
                        new LValueExpression(lValue),
                        new Literal(TypedLiteral.getInt(1)),
                        arithOp), rvalue);
                        */
    }

    @Override
    public Expression getPostMutation() {
        throw new IllegalStateException();
    }

    @Override
    public Expression getPreMutation() {
        throw new IllegalStateException();
    }

    @Override
    public AbstractAssignmentExpression getInliningExpression() {
        return new AssignmentExpression(getCreatedLValue(), getRValue());
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        lvalue = lvalue.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
        rvalue = rvalue.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
        // We need to make sure that we haven't violated any preconditions with a rewrite.
        lValueRewriter.checkPostConditions(lvalue, rvalue);
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        lvalue = expressionRewriter.rewriteExpression(lvalue, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.LVALUE);
        rvalue = expressionRewriter.rewriteExpression(rvalue, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredAssignment(lvalue, rvalue);
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return lvalue.canThrow(caught) || rvalue.canThrow(caught);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AssignmentSimple)) return false;

        AssignmentSimple other = (AssignmentSimple) o;
        return lvalue.equals(other.lvalue) && rvalue.equals(other.rvalue);
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        AssignmentSimple other = (AssignmentSimple) o;
        if (!constraint.equivalent(lvalue, other.lvalue)) return false;
        if (!constraint.equivalent(rvalue, other.rvalue)) return false;
        return true;
    }
}
