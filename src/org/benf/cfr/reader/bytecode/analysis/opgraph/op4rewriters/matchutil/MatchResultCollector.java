package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

public interface MatchResultCollector {
    void clear();

    void collectStatement(String name, StructuredStatement statement);

    void collectMatches(String name, WildcardMatch wcm);
}
