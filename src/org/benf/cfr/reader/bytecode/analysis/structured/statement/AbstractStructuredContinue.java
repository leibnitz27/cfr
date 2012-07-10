package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 10/07/2012
 * Time: 17:51
 */
public abstract class AbstractStructuredContinue extends AbstractStructuredStatement {
    public abstract BlockIdentifier getContinueTgt();
}
