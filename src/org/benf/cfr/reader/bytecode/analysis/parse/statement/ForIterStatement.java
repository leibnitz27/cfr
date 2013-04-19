package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredIter;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:05
 * To change this template use File | Settings | File Templates.
 */
public class ForIterStatement extends AbstractStatement {
    private BlockIdentifier blockIdentifier;
    private LValue iterator;
    private Expression list; // or array!

    public ForIterStatement(BlockIdentifier blockIdentifier, LValue iterator, Expression list) {
        this.blockIdentifier = blockIdentifier;
        this.iterator = iterator;
        this.list = list;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("for (").dump(iterator).print(" : ").dump(list).print(")");
        dumper.print(" // ends " + getTargetStatement(1).getContainer().getLabel() + ";\n");
        return dumper;
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        throw new UnsupportedOperationException("Shouldn't be called here.");
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        iterator = expressionRewriter.rewriteExpression(iterator, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
        list = expressionRewriter.rewriteExpression(list, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredIter(blockIdentifier, iterator, list);
    }

    public BlockIdentifier getBlockIdentifier() {
        return blockIdentifier;
    }

}
