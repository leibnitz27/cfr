package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitch;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitchEntry;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * <p/>
 * A 'raw' switch contains the original bytecode information about the switch.  We split this up into
 * SwitchStatements and CaseStatements.  Case statements are really no more than glorified comments,
 * as they perform no function other than to serve as labels.  However, we can embed useful information in
 * them.
 */
public class RawSwitchStatement extends AbstractStatement {
    private Expression switchOn;
    private final DecodedSwitch switchData;

    public RawSwitchStatement(Expression switchOn, DecodedSwitch switchData) {
        this.switchOn = switchOn;
        this.switchData = switchData;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("switch (" + switchOn + ") {\n");
        List<DecodedSwitchEntry> targets = switchData.getJumpTargets();
        int targetIdx = 1;
        for (DecodedSwitchEntry decodedSwitchEntry : targets) {
            String tgtLbl = getTargetStatement(targetIdx++).getContainer().getLabel();
            dumper.print(" case " + decodedSwitchEntry.getValue() + ": goto " + tgtLbl + ";\n");
        }
        dumper.print(" default: goto " + getTargetStatement(0).getContainer().getLabel() + ";\n");
        dumper.print("}\n");

    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        switchOn = switchOn.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        switchOn = expressionRewriter.rewriteExpression(switchOn, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    public DecodedSwitch getSwitchData() {
        return switchData;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        throw new RuntimeException("Can't convert a raw switch statement to a structured statement");
    }

    public SwitchStatement getSwitchStatement(BlockIdentifier blockIdentifier) {
        return new SwitchStatement(switchOn, switchData, blockIdentifier);
    }


}
