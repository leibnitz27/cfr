package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

public interface Matcher<T> {
    boolean match(MatchIterator<T> matchIterator, MatchResultCollector matchResultCollector);
}
