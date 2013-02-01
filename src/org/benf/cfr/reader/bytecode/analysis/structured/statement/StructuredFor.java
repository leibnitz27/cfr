package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredFor extends AbstractStructuredStatement {
    private ConditionalExpression condition;
    private Op04StructuredStatement body;
    private AssignmentSimple initial;
    private AbstractAssignmentExpression assignment;
    private final BlockIdentifier block;

    public StructuredFor(ConditionalExpression condition, AssignmentSimple initial, AbstractAssignmentExpression assignment, Op04StructuredStatement body, BlockIdentifier block) {
        this.condition = condition;
        this.body = body;
        this.initial = initial;
        this.assignment = assignment;
        this.block = block;
    }

    @Override
    public void dump(Dumper dumper) {
        if (block.hasForeignReferences()) dumper.print(block.getName() + " : ");
        dumper.print("for (" + (initial == null ? "" : initial.toString()) + ";" + condition.toString() + "; " + assignment + ") ");
        body.dump(dumper);
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
        body.transform(transformer);
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        body.linearizeStatementsInto(out);
    }
}
