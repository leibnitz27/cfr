package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 28/01/2013
 * Time: 17:57
 */
public class ResetAfterTest implements Matcher<StructuredStatement> {
    private final WildcardMatch wildcardMatch;
    private final Matcher<StructuredStatement> inner;

    public ResetAfterTest(WildcardMatch wildcardMatch, Matcher<StructuredStatement> inner) {
        this.inner = inner;
        this.wildcardMatch = wildcardMatch;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        boolean result = inner.match(matchIterator, matchResultCollector);
        if (result) matchResultCollector.collectMatches(wildcardMatch);
        wildcardMatch.reset();
        return result;
    }
}
