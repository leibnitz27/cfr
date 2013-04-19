package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
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
        }
        dumper.print(";").dump(condition).print("; ").dump(assignment).print(") ");
        getBody().dump(dumper);
        return dumper;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
        getBody().transform(transformer);
    }


    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        getBody().linearizeStatementsInto(out);
    }


    @Override
    public void traceLocalVariableScope(LValueAssignmentScopeDiscoverer scopeDiscoverer) {
        // While it's not strictly speaking 2 blocks, we can model it as the statement / definition
        // section of the for as being an enclosing block.  (otherwise we add the variable in the wrong scope).
        scopeDiscoverer.enterBlock();
        if (initial != null) {
            LValue lValue = initial.getCreatedLValue();
            Expression expression = initial.getRValue();
            lValue.collectLValueAssignments(expression, this.getContainer(), scopeDiscoverer);
        }
        super.traceLocalVariableScope(scopeDiscoverer);
        scopeDiscoverer.leaveBlock();
    }

    @Override
    public void markCreator(LocalVariable localVariable) {
        this.isCreator = true;
    }


    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        condition = expressionRewriter.rewriteExpression(condition, null, this.getContainer(), null);
    }

}
