package org.benf.cfr.reader.bytecode.analysis.parse.wildcard;

public interface Wildcard<X> {
    X getMatch();

    void resetMatch();
}
