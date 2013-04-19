package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredSynchronized;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * To change this template use File | Settings | File Templates.
 */
public class MonitorEnterStatement extends AbstractStatement {
    private Expression monitor;
    private final BlockIdentifier blockIdentifier;

    public MonitorEnterStatement(Expression monitor, BlockIdentifier blockIdentifier) {
        this.monitor = monitor;
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("MONITORENTER : ").dump(monitor).print(" [" + blockIdentifier + "]\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        monitor = monitor.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        monitor = expressionRewriter.rewriteExpression(monitor, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    public Expression getMonitor() {
        return monitor;
    }

    public BlockIdentifier getBlockIdentifier() {
        return blockIdentifier;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredSynchronized(monitor, blockIdentifier);
    }
}
