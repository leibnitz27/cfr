package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

public class KleeneStar extends KleeneN {
    public KleeneStar(Matcher<StructuredStatement> inner) {
        super(0, inner);
    }

    public KleeneStar(Matcher<StructuredStatement>... matchers) {
        super(0, matchers);
    }
}
