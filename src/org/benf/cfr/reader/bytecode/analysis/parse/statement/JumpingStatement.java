package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;

public abstract class JumpingStatement extends AbstractStatement {
    public JumpingStatement(BytecodeLoc loc) {
        super(loc);
    }

    public abstract Statement getJumpTarget();

    public abstract JumpType getJumpType();

    public abstract void setJumpType(JumpType jumpType);

    public abstract boolean isConditional();
}
