package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

/**
 * Negate ONE STATEMENT.
 */
public class Negated implements Matcher<StructuredStatement> {
    Matcher<StructuredStatement> inner;

    public Negated(Matcher<StructuredStatement> inner) {
        this.inner = inner;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        MatchIterator<StructuredStatement> mi = matchIterator.copy();

        if (inner.match(mi, matchResultCollector)) return false;

        matchIterator.advance();
        return true;
    }
}
