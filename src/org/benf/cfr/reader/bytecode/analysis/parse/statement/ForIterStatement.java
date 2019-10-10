package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredIter;
import org.benf.cfr.reader.util.output.Dumper;

public class ForIterStatement extends AbstractStatement {
    private BlockIdentifier blockIdentifier;
    private LValue iterator;
    private Expression list; // or array!
    private LValue hiddenList;

    public ForIterStatement(BlockIdentifier blockIdentifier, LValue iterator, Expression list, LValue hiddenList) {
        this.blockIdentifier = blockIdentifier;
        this.iterator = iterator;
        this.list = list;
        this.hiddenList = hiddenList;
    }

    @Override
    public LValue getCreatedLValue() {
        return iterator;
    }

    public Expression getList() {
        return list;
    }

    public LValue getHiddenList() {
        return hiddenList;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("for (");
        if (iterator.isFinal()) dumper.print("final ");
        dumper.dump(iterator).print(" : ").dump(list).separator(")");
        dumper.print(" // ends " + getTargetStatement(1).getContainer().getLabel() + ";").newln();
        return dumper;
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        iterator.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        iterator = expressionRewriter.rewriteExpression(iterator, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
        list = expressionRewriter.rewriteExpression(list, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        list.collectUsedLValues(lValueUsageCollector);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredIter(blockIdentifier, iterator, list);
    }

    public BlockIdentifier getBlockIdentifier() {
        return blockIdentifier;
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        ForIterStatement other = (ForIterStatement) o;
        if (!constraint.equivalent(iterator, other.iterator)) return false;
        if (!constraint.equivalent(list, other.list)) return false;
        return true;
    }


}
