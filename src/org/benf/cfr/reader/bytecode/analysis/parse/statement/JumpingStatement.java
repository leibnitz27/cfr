package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;

/**
 * Created:
 * User: lee
 * Date: 02/05/2012
 */
public abstract class JumpingStatement extends AbstractStatement {
    public abstract Statement getJumpTarget();

    public abstract JumpType getJumpType();

    public abstract void setJumpType(JumpType jumpType);

    public abstract boolean isConditional();
}
