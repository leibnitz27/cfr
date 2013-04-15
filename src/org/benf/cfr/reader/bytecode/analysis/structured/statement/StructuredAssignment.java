package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;


/**
 * Created:
 * User: lee
 * Date: 14/05/2012
 */
public class StructuredAssignment extends AbstractStructuredStatement {

    private LValue lvalue;
    private Expression rvalue;
    boolean isCreator;

    public StructuredAssignment(LValue lvalue, Expression rvalue) {
        this.lvalue = lvalue;
        this.rvalue = rvalue;
        this.isCreator = false;
    }

    @Override
    public void dump(Dumper dumper) {
        if (isCreator) {
            dumper.print(lvalue.getInferredJavaType().getJavaTypeInstance().toString() + " ");
        }
        dumper.print(lvalue.toString() + " = " + rvalue.toString() + ";\n");
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
    }

    @Override
    public void traceLocalVariableScope(LValueAssignmentScopeDiscoverer scopeDiscoverer) {
        lvalue.collectLValueAssignments(rvalue, getContainer(), scopeDiscoverer);
    }

    @Override
    public void markCreator(LocalVariable localVariable) {
        if (!localVariable.equals(lvalue)) {
            throw new IllegalArgumentException("Being asked to mark creator for wrong variable");
        }
        isCreator = true;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredAssignment)) return false;
        StructuredAssignment other = (StructuredAssignment) o;
        if (!lvalue.equals(other.lvalue)) return false;
        if (!rvalue.equals(other.rvalue)) return false;
        matchIterator.advance();
        return true;
    }


    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        rvalue = expressionRewriter.rewriteExpression(rvalue, null, this.getContainer(), null);
    }

}

