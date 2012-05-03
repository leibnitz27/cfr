package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.GraphConversionHelper;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
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
}
