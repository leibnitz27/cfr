package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;

public abstract class AbstractStructuredContinue extends AbstractStructuredStatement {
    public AbstractStructuredContinue(BytecodeLoc loc) {
        super(loc);
    }

    public abstract BlockIdentifier getContinueTgt();
}
