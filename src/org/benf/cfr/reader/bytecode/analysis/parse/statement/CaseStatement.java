package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredCase;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * To change this template use File | Settings | File Templates.
 */
public class CaseStatement extends AbstractStatement {
    private Expression value; // null for default.
    private final BlockIdentifier switchBlock;
    private final BlockIdentifier caseBlock;

    public CaseStatement(Expression value, BlockIdentifier switchBlock, BlockIdentifier caseBlock) {
        this.value = value;
        this.switchBlock = switchBlock;
        this.caseBlock = caseBlock;
    }

    @Override
    public void dump(Dumper dumper) {
        if (value == null) {
            dumper.print("default:\n");
        } else {
            dumper.print("case " + value + ":\n");
        }
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        if (value == null) return;
        value = value.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredCase(value, caseBlock);
    }

    public BlockIdentifier getCaseBlock() {
        return caseBlock;
    }
}
