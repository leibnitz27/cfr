package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.util.output.Dumper;

public class JSRCallStatement extends AbstractStatement {

    public JSRCallStatement() {
    }

    /*
     * Obviously, we don't want to be emitting this in any language - these should have been transformed away before
     * the final output.
     */
    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("CALL " + getTargetStatement(0).getContainer().getLabel() + ";\n");
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
        return new StructuredComment("JSR Call");
    }


    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public boolean fallsToNext() {
        return false;
    }
}
