package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util;

import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 30/01/2013
 * Time: 17:38
 */
public interface MatchResultCollector {
    void clear();

    void collectStatement(String name, StructuredStatement statement);

    void collectMatches(WildcardMatch wcm);
}
