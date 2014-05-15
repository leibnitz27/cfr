package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;

public abstract class AbstractStructuredContinue extends AbstractStructuredStatement {
    public abstract BlockIdentifier getContinueTgt();
}
