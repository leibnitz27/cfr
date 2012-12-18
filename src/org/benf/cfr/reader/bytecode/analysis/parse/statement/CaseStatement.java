package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredCase;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * To change this template use File | Settings | File Templates.
 */
public class CaseStatement extends AbstractStatement {
    private List<Expression> values; // null for default.
    private final BlockIdentifier switchBlock;
    private final BlockIdentifier caseBlock;

    public CaseStatement(List<Expression> values, BlockIdentifier switchBlock, BlockIdentifier caseBlock) {
        this.values = values;
        this.switchBlock = switchBlock;
        this.caseBlock = caseBlock;
    }

    @Override
    public void dump(Dumper dumper) {
        if (values.isEmpty()) {
            dumper.print("default:\n");
        } else {
            for (Expression value : values) {
                dumper.print("case " + value + ":\n");
            }
        }
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

    public BlockIdentifier getSwitchBlock() {
        return switchBlock;
    }

    public boolean isDefault() {
        return values.isEmpty();
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredCase(values, caseBlock);
    }

    public BlockIdentifier getCaseBlock() {
        return caseBlock;
    }
}
