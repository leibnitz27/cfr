package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/03/2013
 * Time: 06:22
 */
public abstract class AbstractStructuredBlockStatement extends AbstractStructuredStatement {

    private Op04StructuredStatement body;

    public AbstractStructuredBlockStatement(Op04StructuredStatement body) {
        this.body = body;
    }

    protected Op04StructuredStatement getBody() {
        return body;
    }

    @Override
    public boolean isRecursivelyStructured() {
        return body.isFullyStructured();
    }
}
