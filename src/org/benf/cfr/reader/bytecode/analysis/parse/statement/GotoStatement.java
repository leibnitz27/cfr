package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredBreak;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredContinue;
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
    public Dumper dump(Dumper dumper) {
        return dumper.print("" + jumpType + " " + getJumpTarget().getContainer().getLabel() + ";\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
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

    @Override
    public void collectObjectCreation(CreationCollector creationCollector) {
        creationCollector.markJump();
    }

    protected BlockIdentifier getTargetStartBlock() {
        Statement statement = getJumpTarget();
        if (statement instanceof WhileStatement) {
            WhileStatement whileStatement = (WhileStatement) statement;
            return whileStatement.getBlockIdentifier();
        } else if (statement instanceof ForStatement) {
            ForStatement forStatement = (ForStatement) statement;
            return forStatement.getBlockIdentifier();
        } else if (statement instanceof ForIterStatement) {
            ForIterStatement forStatement = (ForIterStatement) statement;
            return forStatement.getBlockIdentifier();
        } else {
            BlockIdentifier blockStarted = statement.getContainer().getBlockStarted();
            if (blockStarted != null && blockStarted.getBlockType() == BlockType.UNCONDITIONALDOLOOP) {
                return blockStarted;
            } else {
                throw new ConfusedCFRException("CONTINUE without a while " + statement.getClass());
            }
        }
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        switch (jumpType) {
            case GOTO:
            case GOTO_OUT_OF_IF:
                return new UnstructuredGoto();
            case CONTINUE:
                return new UnstructuredContinue(getTargetStartBlock());
            case BREAK:
                return new UnstructuredBreak(getJumpTarget().getContainer().getBlocksEnded());
        }
        throw new UnsupportedOperationException();
    }
}
