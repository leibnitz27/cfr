package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/03/2012
 * Time: 17:57
 */
public class AssignmentSimple extends AbstractAssignment {
    private LValue lvalue;
    private Expression rvalue;

    public AssignmentSimple(LValue lvalue, Expression rvalue) {
        this.lvalue = lvalue;
        this.rvalue = rvalue;
//        System.out.println("LValue " + lvalue + " " + lvalue.getInferredJavaType());
//        System.out.println("RValue " + rvalue + " " + rvalue.getInferredJavaType());
        lvalue.getInferredJavaType().chain(rvalue.getInferredJavaType());
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print(this.toString() + ";\n");
    }

    @Override
    public String toString() {
        return (lvalue.toString() + " = " + rvalue.toString());
    }

    @Override
    public void getLValueEquivalences(LValueAssignmentCollector lValueAssigmentCollector) {
        lvalue.determineLValueEquivalence(rvalue, this.getContainer(), lValueAssigmentCollector);
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

    /* We /should/ be using assignmentPreChange here, but if that has been disabled, these
     * assignments should be able to stand in.
     *
     * This (should) also catch self member calls?
     */
    @Override
    public boolean isSelfMutatingOperation() {
        if (rvalue instanceof ArithmeticOperation) {
            ArithmeticOperation arithmeticOperation = (ArithmeticOperation) rvalue;
            if (arithmeticOperation.isLiteralFunctionOf(lvalue)) return true;
        }
        return false;
    }

    @Override
    public boolean isSelfMutatingIncr1(LValue lValue) {
        if (!lValue.equals(this.lvalue)) return false;
        WildcardMatch wildcardMatch = new WildcardMatch();

        return wildcardMatch.match(
                new ArithmeticOperation(
                        new LValueExpression(lValue),
                        new Literal(TypedLiteral.getInt(1)),
                        ArithOp.PLUS), rvalue);
    }

    @Override
    public AbstractAssignmentExpression getInliningExpression() {
        return new AssignmentExpression(getCreatedLValue(), getRValue());
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        lvalue = lvalue.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
        rvalue = rvalue.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredAssignment(lvalue, rvalue);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AssignmentSimple)) return false;

        AssignmentSimple other = (AssignmentSimple) o;
        return lvalue.equals(other.lvalue) && rvalue.equals(other.rvalue);
    }
}
