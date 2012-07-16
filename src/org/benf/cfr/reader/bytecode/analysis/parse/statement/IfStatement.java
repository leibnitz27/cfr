package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BoolOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NotOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.ConditionalUtils;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredIf;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredBreak;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredContinue;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredIf;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:05
 * To change this template use File | Settings | File Templates.
 */
public class IfStatement extends GotoStatement {

    private static final int JUMP_NOT_TAKEN = 0;
    private static final int JUMP_TAKEN = 1;

    private ConditionalExpression condition;
    private BlockIdentifier knownIfBlock = null;
    private BlockIdentifier knownElseBlock = null;


    public IfStatement(ConditionalExpression conditionalExpression) {
        this.condition = conditionalExpression;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("if (" + condition.toString() + ") ");
        super.dump(dumper);
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        Expression replacementCondition = condition.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
        if (replacementCondition != condition) {
            this.condition = (ConditionalExpression) replacementCondition;
        }
    }

    @Override
    public boolean condenseWithNextConditional() {
        // Get the next (fall through) statement.  If that's not a conditional, ignore.
        // Since the next statement is ALWAYS fall through, we don't need to test that.
        Statement nextStatement = getTargetStatement(JUMP_NOT_TAKEN);
        return nextStatement.condenseWithPriorIfStatement(this);
    }

    public ConditionalExpression getCondition() {
        return condition;
    }

    public void setCondition(ConditionalExpression condition) {
        this.condition = condition;
    }

    public void simplifyCondition() {
        condition = ConditionalUtils.simplify(condition);
    }

    public void negateCondition() {
        condition = ConditionalUtils.simplify(condition.getNegated());
    }

    @Override
    public boolean condenseWithPriorIfStatement(IfStatement prior) {
        Statement fallThrough2 = getTargetStatement(JUMP_NOT_TAKEN);
        Statement target1 = prior.getTargetStatement(JUMP_TAKEN);

        // if (c1) goto a
        // if (c2) goto b
        // a
        // ->
        // if (!c1 && c2) goto b
        if (fallThrough2 == target1) {
            this.condition = new BooleanOperation(new NotOperation(prior.getCondition()), getCondition(), BoolOp.AND);
            prior.getContainer().nopOutConditional();
            return true;
        }
        // if (c1) goto a
        // if (c2) goto a
        // b
        // ->
        // if (c1 || c2) goto a
        Statement target2 = getTargetStatement(JUMP_TAKEN);
        if (target1 == target2) {
            this.condition = new BooleanOperation(prior.getCondition(), getCondition(), BoolOp.OR);
            prior.getContainer().nopOutConditional();
            return true;
        }
        return false;
    }


    public void replaceWithWhileLoopStart(BlockIdentifier blockIdentifier) {
        WhileStatement replacement = new WhileStatement(ConditionalUtils.simplify(condition.getNegated()), blockIdentifier);
        getContainer().replaceStatement(replacement);
    }

    public void replaceWithWhileLoopEnd(BlockIdentifier blockIdentifier) {
        WhileStatement replacement = new WhileStatement(ConditionalUtils.simplify(condition), blockIdentifier);
        getContainer().replaceStatement(replacement);
    }

    @Override
    public Statement getJumpTarget() {
        return getTargetStatement(JUMP_TAKEN);
    }

    @Override
    public boolean isConditional() {
        return true;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        switch (getJumpType()) {
            case GOTO:
            case GOTO_KNOWN:
                return new UnstructuredIf(condition, knownIfBlock, knownElseBlock);
            case CONTINUE:
                return new StructuredIf(condition, new Op04StructuredStatement(new UnstructuredContinue(getTargetStartBlock())));
            case BREAK:
                return new StructuredIf(condition, new Op04StructuredStatement(new UnstructuredBreak(getJumpTarget().getContainer().getBlocksEnded())));
        }
        throw new UnsupportedOperationException();
    }

    public void setKnownBlocks(BlockIdentifier ifBlock, BlockIdentifier elseBlock) {
        this.knownIfBlock = ifBlock;
        this.knownElseBlock = elseBlock;
    }

    public void optimiseForTypes() {
        condition = condition.optimiseForType();
    }
}
