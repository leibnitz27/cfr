package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;

public abstract class MonitorStatement extends AbstractStatement {
    public MonitorStatement(BytecodeLoc loc) {
        super(loc);
    }
}
