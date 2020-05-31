package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredTry;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Set;

public class TryStatement extends AbstractStatement {
    private final ExceptionGroup exceptionGroup;
    // This is a hack. :(
    // We keep track of what mutexes this finally leaves.
    private final Set<Expression> monitors = SetFactory.newSet();

    public TryStatement(ExceptionGroup exceptionGroup) {
        this.exceptionGroup = exceptionGroup;
    }

    public void addExitMutex(Expression e) {
        monitors.add(e);
    }

    public Set<Expression> getMonitors() {
        return monitors;
    }

    @Override
    public Statement deepClone(CloneHelper cloneHelper) {
        TryStatement res = new TryStatement(exceptionGroup);
        for (Expression monitor : monitors) {
            res.monitors.add(cloneHelper.replaceOrClone(monitor));
        }
        return res;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("try { ").print(exceptionGroup.getTryBlockIdentifier().toString()).newln();
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredTry(exceptionGroup);
    }

    public BlockIdentifier getBlockIdentifier() {
        return exceptionGroup.getTryBlockIdentifier();
    }

    public List<ExceptionGroup.Entry> getEntries() {
        return exceptionGroup.getEntries();
    }

    public boolean equivalentUnder(Object other, EquivalenceConstraint constraint) {
        return this.getClass() == other.getClass();
    }
}
