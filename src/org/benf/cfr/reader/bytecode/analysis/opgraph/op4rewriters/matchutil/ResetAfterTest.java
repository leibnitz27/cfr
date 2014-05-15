package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

public class ResetAfterTest implements Matcher<StructuredStatement> {
    private final WildcardMatch wildcardMatch;
    private final Matcher<StructuredStatement> inner;
    private final String name;

    public ResetAfterTest(WildcardMatch wildcardMatch, Matcher<StructuredStatement> inner) {
        this(wildcardMatch, "", inner);
    }

    public ResetAfterTest(WildcardMatch wildcardMatch, String name, Matcher<StructuredStatement> inner) {
        this.inner = inner;
        this.wildcardMatch = wildcardMatch;
        this.name = name;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        boolean result = inner.match(matchIterator, matchResultCollector);
        if (result) matchResultCollector.collectMatches(name, wildcardMatch);
        wildcardMatch.reset();
        return result;
    }
}
