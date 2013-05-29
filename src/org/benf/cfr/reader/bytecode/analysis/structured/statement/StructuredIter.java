package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredIter extends AbstractStructuredBlockStatement {
    private final BlockIdentifier block;
    private LValue iterator;
    private Expression list;

    public StructuredIter(BlockIdentifier block, LValue iterator, Expression list, Op04StructuredStatement body) {
        super(body);
        this.block = block;
        this.iterator = iterator;
        this.list = list;
        /*
         * We need to be able to type the iterator.
         */
        JavaTypeInstance itertype = iterator.getInferredJavaType().getJavaTypeInstance();
        if (!itertype.isUsableType()) {
            //      throw new ConfusedCFRException("Not a usable type for an iter");
        }
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (block.hasForeignReferences()) dumper.print(block.getName() + " : ");
        JavaTypeInstance itertype = iterator.getInferredJavaType().getJavaTypeInstance();
        dumper.print("for (" + itertype + " ").dump(iterator).print(" : ").dump(list).print(") ");
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
        iterator.collectLValueAssignments(null, this.getContainer(), scopeDiscoverer);
        super.traceLocalVariableScope(scopeDiscoverer);
        scopeDiscoverer.leaveBlock();
    }

    @Override
    public void markCreator(LocalVariable localVariable) {
        // Nop.  Structured iter is always the creator.
    }


    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        iterator = expressionRewriter.rewriteExpression(iterator, null, this.getContainer(), null);
        list = expressionRewriter.rewriteExpression(list, null, this.getContainer(), null);
    }

}
