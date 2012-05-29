package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredBreak;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredContinue;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredGoto;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * To change this template use File | Settings | File Templates.
 */
public class GotoStatement extends JumpingStatement {

    private JumpType jumpType;

    public GotoStatement() {
        this.jumpType = JumpType.GOTO;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("" + jumpType + " " + getJumpTarget().getContainer().getLabel() + ";\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueCollector lValueCollector, SSAIdentifiers ssaIdentifiers) {
    }

    @Override
    public JumpType getJumpType() {
        return jumpType;
    }

    @Override
    public void setJumpType(JumpType jumpType) {
        this.jumpType = jumpType;
    }

    @Override
    public Statement getJumpTarget() {
        return getTargetStatement(0);
    }

    @Override
    public boolean isConditional() {
        return false;
    }

    protected BlockIdentifier getTargetStartBlock() {
        Statement statement = getJumpTarget();
        if (statement instanceof WhileStatement) {
            WhileStatement whileStatement = (WhileStatement) statement;
            return whileStatement.getBlockIdentifier();
        } else {
            throw new ConfusedCFRException("CONTINUE without a while");
        }
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        switch (jumpType) {
            case GOTO:
                return new UnstructuredGoto();
            case CONTINUE:
                return new StructuredContinue(getTargetStartBlock());
            case BREAK:
                return new StructuredBreak();
        }
        throw new UnsupportedOperationException();
    }
}
