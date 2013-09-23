package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredCatch;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
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
public class CatchStatement extends AbstractStatement {
    private final List<ExceptionGroup.Entry> exceptions;
    private BlockIdentifier catchBlockIdent;
    private LValue catching;

    public CatchStatement(List<ExceptionGroup.Entry> exceptions, LValue catching) {
        this.exceptions = exceptions;
        this.catching = catching;
        if (!exceptions.isEmpty()) {
            InferredJavaType catchType = new InferredJavaType(exceptions.get(0).getCatchType(), InferredJavaType.Source.EXCEPTION, true);
            this.catching.getInferredJavaType().chain(catchType);
        }
    }

    public void removeCatchBlockFor(final BlockIdentifier tryBlockIdent) {
        List<ExceptionGroup.Entry> toRemove = Functional.filter(exceptions, new Predicate<ExceptionGroup.Entry>() {
            @Override
            public boolean test(ExceptionGroup.Entry in) {
                return in.getTryBlockIdentifier().equals(tryBlockIdent);
            }
        });
        exceptions.removeAll(toRemove);
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("catch ( " + exceptions + " ").dump(catching).print(" ) {\n");
    }

    public BlockIdentifier getCatchBlockIdent() {
        return catchBlockIdent;
    }

    public void setCatchBlockIdent(BlockIdentifier catchBlockIdent) {
        this.catchBlockIdent = catchBlockIdent;
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
    public void collectLValueAssignments(LValueAssignmentCollector<Statement> lValueAssigmentCollector) {
        if (catching instanceof LocalVariable) {
            lValueAssigmentCollector.collectLocalVariableAssignment((LocalVariable) catching, this.getContainer(), null);
        }
    }

    @Override
    public LValue getCreatedLValue() {
        return catching;
    }

    public List<ExceptionGroup.Entry> getExceptions() {
        return exceptions;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredCatch(exceptions, catchBlockIdent, catching);
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        CatchStatement other = (CatchStatement) o;
        if (!constraint.equivalent(exceptions, other.exceptions)) return false;
        if (!constraint.equivalent(catching, other.catching)) return false;
        return true;
    }

}
