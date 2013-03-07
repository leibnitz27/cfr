package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractMutatingAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithOp;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/03/2012
 * Time: 17:57
 * <p/>
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

    @Override
    public void dump(Dumper dumper) {
        dumper.print(this.toString() + ";\n");
    }

    @Override
    public String toString() {
        return (rvalue.toString());
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
    public void collectObjectCreation(CreationCollector creationCollector) {
        creationCollector.collectCreation(lvalue, rvalue, this.getContainer());
    }

    @Override
    public SSAIdentifiers collectLocallyMutatedVariables(SSAIdentifierFactory ssaIdentifierFactory) {
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
    public AbstractAssignmentExpression getInliningExpression() {
        return rvalue;
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        lvalue = lvalue.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
        rvalue = (AbstractAssignmentExpression) rvalue.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        lvalue = expressionRewriter.rewriteExpression(lvalue, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.LVALUE);
        rvalue = expressionRewriter.rewriteExpression(rvalue, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredExpressionStatement(rvalue);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AssignmentPreMutation)) return false;

        AssignmentPreMutation other = (AssignmentPreMutation) o;
        return lvalue.equals(other.lvalue) && rvalue.equals(other.rvalue);
    }
}
