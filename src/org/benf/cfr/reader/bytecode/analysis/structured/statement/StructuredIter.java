package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ListFactory;
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
    private boolean creator;

    public StructuredIter(BlockIdentifier block, LValue iterator, Expression list, Op04StructuredStatement body) {
        super(body);
        this.block = block;
        this.iterator = iterator;
        this.list = list;
        this.creator = false;
        /*
         * We need to be able to type the iterator.
         */
        JavaTypeInstance itertype = iterator.getInferredJavaType().getJavaTypeInstance();
        if (!itertype.isUsableType()) {
            //      throw new ConfusedCFRException("Not a usable type for an iter");
        }
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        iterator.collectTypeUsages(collector);
        list.collectTypeUsages(collector);
        super.collectTypeUsages(collector);
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (block.hasForeignReferences()) dumper.print(block.getName() + " : ");
        JavaTypeInstance itertype = iterator.getInferredJavaType().getJavaTypeInstance();
        dumper.print("for (");
        dumper.dump(itertype).print(" ");
        dumper.dump(iterator).print(" : ").dump(list).print(") ");
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
        list.collectUsedLValues(scopeDiscoverer);
        iterator.collectLValueAssignments(null, this.getContainer(), scopeDiscoverer);
        getBody().traceLocalVariableScope(scopeDiscoverer);
        scopeDiscoverer.leaveBlock(this);
    }

    @Override
    public void markCreator(LocalVariable localVariable) {
        // we're always creator.  But we could verify additionally.
        creator = true;
    }

    @Override
    public boolean alwaysDefines(LocalVariable localVariable) {
        // Could check!
        return true;
    }

    @Override
    public List<LocalVariable> findCreatedHere() {
        if (!(iterator instanceof LocalVariable)) return null;
        return ListFactory.newList((LocalVariable) iterator);
    }


    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        iterator = expressionRewriter.rewriteExpression(iterator, null, this.getContainer(), null);
        list = expressionRewriter.rewriteExpression(list, null, this.getContainer(), null);
    }

}
