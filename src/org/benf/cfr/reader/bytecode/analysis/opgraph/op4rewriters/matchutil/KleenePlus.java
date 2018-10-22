package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

public class KleenePlus extends KleeneN {
    public KleenePlus(Matcher<StructuredStatement> inner) {
        super(1, inner);
    }
}
