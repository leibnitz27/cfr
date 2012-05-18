package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitch;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitchEntry;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredSwitch extends AbstractStructuredStatement {
    private Expression switchOn;
    private final DecodedSwitch switchData;

    public UnstructuredSwitch(Expression switchOn, DecodedSwitch switchData) {
        this.switchOn = switchOn;
        this.switchData = switchData;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("switch (" + switchOn + ") {\n");
        List<DecodedSwitchEntry> targets = switchData.getJumpTargets();
//        int targetIdx = 1;
        for (DecodedSwitchEntry decodedSwitchEntry : targets) {
//            String tgtLbl = getTargetStatement(targetIdx++).getContainer().getLabel();
            dumper.print(" case " + decodedSwitchEntry.getValue() + ":\n"); // : goto " + tgtLbl + ";\n");
        }
        dumper.print(" default: \n"); // goto " + getTargetStatement(0).getContainer().getLabel() + ";\n");
        dumper.print("}\n");
    }

    @Override
    public boolean isProperlyStructured() {
        return false;
    }

}
