package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.GraphConversionHelper;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BoolOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NotOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.util.ConfusedCFRException;
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


    public IfStatement(ConditionalExpression conditionalExpression) {
        this.condition = conditionalExpression;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("if (" + condition.toString() + ") ");
        super.dump(dumper);
    }

    @Override
    public void replaceSingleUsageLValues(LValueCollector lValueCollector, SSAIdentifiers ssaIdentifiers) {
        Expression replacementCondition = condition.replaceSingleUsageLValues(lValueCollector, ssaIdentifiers);
        if (replacementCondition != condition) throw new ConfusedCFRException("Can't yet support replacing conditions");
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
        WhileStatement replacement = new WhileStatement(condition.getNegatedExpression());
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
}
