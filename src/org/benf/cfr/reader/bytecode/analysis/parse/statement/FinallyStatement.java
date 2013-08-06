package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredCatch;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredFinally;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.Predicate;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * To change this template use File | Settings | File Templates.
 */
public class FinallyStatement extends AbstractStatement {
    private BlockIdentifier finallyBlockIdent;

    public FinallyStatement(BlockIdentifier finallyBlockIdent) {
        this.finallyBlockIdent = finallyBlockIdent;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("finally {\n");
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
    public LValue getCreatedLValue() {
        return null;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredFinally(finallyBlockIdent);
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        FinallyStatement other = (FinallyStatement) o;
//        if (!constraint.equivalent(finallyBlockIdent, other.finallyBlockIdent)) return false;
        return true;
    }

}
