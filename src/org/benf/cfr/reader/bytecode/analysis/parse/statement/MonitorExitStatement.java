package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * To change this template use File | Settings | File Templates.
 */
public class MonitorExitStatement extends AbstractStatement {
    private Expression monitor;

    public MonitorExitStatement(Expression monitor) {
        this.monitor = monitor;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("MONITOREXIT : " + monitor + "\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        monitor = monitor.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        monitor = expressionRewriter.rewriteExpression(monitor, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public String toString() {
        return "MonitorExit : " + monitor;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredComment("** MonitorExit[" + monitor + "] (shouldn't be in output)");
    }

    public Expression getMonitor() {
        return monitor;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof MonitorExitStatement)) return false;
        MonitorExitStatement other = (MonitorExitStatement) o;
        return monitor.equals(other.monitor);
    }
}
