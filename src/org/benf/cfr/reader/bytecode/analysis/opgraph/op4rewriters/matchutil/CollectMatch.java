package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

public class CollectMatch implements Matcher<StructuredStatement> {
    private final Matcher<StructuredStatement> inner;
    private final String name;

    public CollectMatch(String name, Matcher<StructuredStatement> inner) {
        this.inner = inner;
        this.name = name;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        MatchIterator<StructuredStatement> orig = matchIterator.copy();
        boolean res = inner.match(matchIterator, matchResultCollector);
        if (res) {
            matchResultCollector.collectStatement(name, orig.getCurrent());
        }
        return res;
    }
}
