package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.state.TypeUsageCollector;

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

    public Op04StructuredStatement getBody() {
        return body;
    }

    @Override
    public boolean isRecursivelyStructured() {
        return body.isFullyStructured();
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        body.collectTypeUsages(collector);
    }
}
