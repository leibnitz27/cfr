package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredSwitch;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitch;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * To change this template use File | Settings | File Templates.
 */
public class SwitchStatement extends AbstractStatement {
    private Expression switchOn;
    private final DecodedSwitch switchData;
    private final BlockIdentifier switchBlock;

    public SwitchStatement(Expression switchOn, DecodedSwitch switchData, BlockIdentifier switchBlock) {
        this.switchOn = switchOn;
        this.switchData = switchData;
        this.switchBlock = switchBlock;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("switch (").dump(switchOn).print(") { // " + switchBlock + "\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        switchOn = switchOn.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        switchOn = expressionRewriter.rewriteExpression(switchOn, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredSwitch(switchOn, switchBlock);
    }

    public BlockIdentifier getSwitchBlock() {
        return switchBlock;
    }
}
