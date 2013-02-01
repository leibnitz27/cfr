package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 25/01/2013
 * Time: 08:35
 */
public interface Matcher<T> {
    boolean match(MatchIterator<T> matchIterator, MatchResultCollector matchResultCollector);
}
