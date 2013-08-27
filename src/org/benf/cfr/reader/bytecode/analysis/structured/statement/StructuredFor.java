package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.Predicate;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredFor extends AbstractStructuredBlockStatement {
    private ConditionalExpression condition;
    private AssignmentSimple initial;
    private AbstractAssignmentExpression assignment;
    private final BlockIdentifier block;
    private boolean isCreator;

    public StructuredFor(ConditionalExpression condition, AssignmentSimple initial, AbstractAssignmentExpression assignment, Op04StructuredStatement body, BlockIdentifier block) {
        super(body);
        this.condition = condition;
        this.initial = initial;
        this.assignment = assignment;
        this.block = block;
        this.isCreator = false;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (block.hasForeignReferences()) dumper.print(block.getName() + " : ");
        dumper.print("for (");
        if (initial != null) {
            // It's a big grotty to have creator here, but no worse that pushing it into Assignmentsimple
            if (isCreator) {
                dumper.print(initial.getCreatedLValue().getInferredJavaType().getJavaTypeInstance().toString() + " ");
            }
            dumper.dump(initial);
            dumper.removePendingCarriageReturn();
        } else {
            dumper.print(";");
        }
        dumper.print(" ").dump(condition).print("; ").dump(assignment).print(") ");
        getBody().dump(dumper);
        return dumper;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
        scope.add(this);
        try {
            getBody().transform(transformer, scope);
        } finally {
            scope.remove(this);
        }
    }


    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        getBody().linearizeStatementsInto(out);
    }


    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        // While it's not strictly speaking 2 blocks, we can model it as the statement / definition
        // section of the for as being an enclosing block.  (otherwise we add the variable in the wrong scope).
        scopeDiscoverer.enterBlock(this);
        assignment.collectUsedLValues(scopeDiscoverer);
        condition.collectUsedLValues(scopeDiscoverer);
        if (initial != null) {
            LValue lValue = initial.getCreatedLValue();
            Expression expression = initial.getRValue();
            lValue.collectLValueAssignments(expression, this.getContainer(), scopeDiscoverer);
        }
        getBody().traceLocalVariableScope(scopeDiscoverer);
        scopeDiscoverer.leaveBlock(this);
    }

    @Override
    public void markCreator(LocalVariable localVariable) {
        this.isCreator = true;
    }

    @Override
    public List<LocalVariable> findCreatedHere() {
        if (!isCreator) return null;
        LValue created = initial.getCreatedLValue();
        if (!(created instanceof LocalVariable)) return null;
        return ListFactory.newList((LocalVariable) created);
    }

    @Override
    public String suggestName(LocalVariable createdHere, Predicate<String> testNameUsedFn) {
        JavaTypeInstance loopType = createdHere.getInferredJavaType().getJavaTypeInstance();

        if (!(assignment instanceof AbstractMutatingAssignmentExpression)) return null;

        if (!(loopType instanceof RawJavaType)) return null;
        RawJavaType rawJavaType = (RawJavaType) loopType;
        switch (rawJavaType) {
            case INT:
            case SHORT:
            case LONG:
                break;
            default:
                return null;
        }
        String[] poss = {"i", "j", "k"};
        for (String posss : poss) {
            if (!testNameUsedFn.test(posss)) {
                return posss;
            }
        }
        return "i"; // And we'll
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        condition = expressionRewriter.rewriteExpression(condition, null, this.getContainer(), null);
    }

}
