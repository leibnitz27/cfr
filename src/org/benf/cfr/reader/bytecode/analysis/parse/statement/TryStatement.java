package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredTry;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * To change this template use File | Settings | File Templates.
 */
public class TryStatement extends AbstractStatement {
    private final ExceptionGroup exceptionGroup;

    public TryStatement(ExceptionGroup exceptionGroup) {
        this.exceptionGroup = exceptionGroup;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("try {\n");
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

    @Override
    public boolean equivalentUnder(Object other, EquivalenceConstraint constraint) {
        return this.getClass() == other.getClass();
    }
}
