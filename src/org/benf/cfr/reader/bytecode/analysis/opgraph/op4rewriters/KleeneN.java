package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 28/01/2013
 * Time: 17:57
 */
public class KleeneN implements Matcher<StructuredStatement> {
    private final Matcher<StructuredStatement> inner;
    private final int nRequired;

    public KleeneN(int nRequired, Matcher<StructuredStatement> inner) {
        this.inner = inner;
        this.nRequired = nRequired;
    }

    public KleeneN(int nRequired, Matcher<StructuredStatement>... matchers) {
        this.inner = new MatchSequence(matchers);
        this.nRequired = nRequired;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        MatchIterator<StructuredStatement> mi = matchIterator.copy();

        int nMatches = 0;
        while (inner.match(mi, matchResultCollector)) {
            nMatches++;
        }

        if (nMatches >= nRequired) {
            matchIterator.advanceTo(mi);
            return true;
        }
        return false;
    }
}
