package org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 24/01/2013
 * Time: 06:04
 */
public class EndBlock extends AbstractPlaceholder {
    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        if (matchIterator.getCurrent() instanceof EndBlock) {
            matchIterator.advance();
            return true;
        }
        return false;
    }
}
