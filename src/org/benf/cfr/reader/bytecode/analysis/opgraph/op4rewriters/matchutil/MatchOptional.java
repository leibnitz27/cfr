package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

public class MatchOptional implements Matcher<StructuredStatement> {
    private final Matcher<StructuredStatement> inner;

    public MatchOptional(Matcher<StructuredStatement> inner) {
        this.inner = inner;
    }

    public MatchOptional(Matcher<StructuredStatement>... matchers) {
        this.inner = new MatchSequence(matchers);
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        MatchIterator<StructuredStatement> mi = matchIterator.copy();

        if (inner.match(mi, matchResultCollector)) {
            matchIterator.advanceTo(mi);
        }
        return true;
    }
}
