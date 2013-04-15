package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredDo;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * To change this template use File | Settings | File Templates.
 */
public class DoStatement extends AbstractStatement {
    private final BlockIdentifier blockIdentifier;

    public DoStatement(BlockIdentifier blockIdentifier) {
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("do\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
    }

    public BlockIdentifier getBlockIdentifier() {
        return blockIdentifier;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredDo(blockIdentifier);
    }
}
