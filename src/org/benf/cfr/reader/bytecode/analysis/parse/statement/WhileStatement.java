package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredWhile;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:05
 * To change this template use File | Settings | File Templates.
 */
public class WhileStatement extends AbstractStatement {
    private ConditionalExpression condition;
    private BlockIdentifier blockIdentifier;

    public WhileStatement(ConditionalExpression conditionalExpression, BlockIdentifier blockIdentifier) {
        this.condition = conditionalExpression;
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("while (" + condition.toString() + ") ");
        dumper.print(" // ends " + getTargetStatement(1).getContainer().getLabel() + ";\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueCollector lValueCollector, SSAIdentifiers ssaIdentifiers) {
        Expression replacementCondition = condition.replaceSingleUsageLValues(lValueCollector, ssaIdentifiers);
        if (replacementCondition != condition) throw new ConfusedCFRException("Can't yet support replacing conditions");
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredWhile(condition, blockIdentifier);
    }

    public BlockIdentifier getBlockIdentifier() {
        return blockIdentifier;
    }
}
