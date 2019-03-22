package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

/**
 * Note that this will match the FIRST match, not necessarily the best.
 * I.e. no backtracking.
 */
public class MatchOpt implements Matcher<StructuredStatement> {
    private final Matcher<StructuredStatement> matcher;

    public MatchOpt(Matcher<StructuredStatement> matcher) {
        this.matcher = matcher;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        MatchIterator<StructuredStatement> mi = matchIterator.copy();
        if (matcher.match(mi, matchResultCollector)) {
            matchIterator.advanceTo(mi);
            return true;
        }
        return true;
    }
}
