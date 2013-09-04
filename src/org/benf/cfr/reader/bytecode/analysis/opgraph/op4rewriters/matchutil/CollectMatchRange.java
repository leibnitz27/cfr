package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 30/01/2013
 * Time: 17:43
 */
public class CollectMatchRange implements Matcher<StructuredStatement> {
    private final Matcher<StructuredStatement> inner;
    private final String name;

    public CollectMatchRange(String name, Matcher<StructuredStatement> inner) {
        this.inner = inner;
        this.name = name;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        MatchIterator<StructuredStatement> orig = matchIterator.copy();
        boolean res = inner.match(matchIterator, matchResultCollector);
        if (res) {
            MatchIterator<StructuredStatement> end = matchIterator.copy();
            matchResultCollector.collectStatementRange(name, orig, end);
        }
        return res;
    }
}
