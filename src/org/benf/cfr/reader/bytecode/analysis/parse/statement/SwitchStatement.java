package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssigmentCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredSwitch;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitch;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitchEntry;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

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

    public SwitchStatement(Expression switchOn, DecodedSwitch switchData) {
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
    public void replaceSingleUsageLValues(LValueAssigmentCollector lValueAssigmentCollector, SSAIdentifiers ssaIdentifiers) {
        switchOn = switchOn.replaceSingleUsageLValues(lValueAssigmentCollector, ssaIdentifiers);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredSwitch(switchOn, switchData);
    }
}
