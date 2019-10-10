package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredCase;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class CaseStatement extends AbstractStatement {
    private List<Expression> values; // null for default.
    private final BlockIdentifier switchBlock;
    private final BlockIdentifier caseBlock;
    private final InferredJavaType caseType;

    public CaseStatement(List<Expression> values, InferredJavaType caseType, BlockIdentifier switchBlock, BlockIdentifier caseBlock) {
        this.values = values;
        this.caseType = caseType;
        this.switchBlock = switchBlock;
        this.caseBlock = caseBlock;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (values.isEmpty()) {
            dumper.print("default").operator(":").newln();
        } else {
            for (Expression value : values) {
                dumper.print("case ").dump(value).operator(":").newln();
            }
        }
        return dumper;
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        for (int x = 0; x < values.size(); ++x) {
            values.set(x, values.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer()));
        }
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        for (int x = 0; x < values.size(); ++x) {
            values.set(x, expressionRewriter.rewriteExpression(values.get(x), ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE));
        }
    }

    @Override
    public void collectLValueUsage(LValueUsageCollector lValueUsageCollector) {
        /* Have to be a constant, so can't be values */
    }

    public BlockIdentifier getSwitchBlock() {
        return switchBlock;
    }

    public boolean isDefault() {
        return values.isEmpty();
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredCase(values, caseType, caseBlock);
    }

    public BlockIdentifier getCaseBlock() {
        return caseBlock;
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return false;
    }

    @Override
    public final boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (getClass() != o.getClass()) return false;
        CaseStatement other = (CaseStatement) o;
        if (!constraint.equivalent(values, other.values)) return false;
        return true;
    }
}
