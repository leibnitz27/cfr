package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 28/01/2013
 * Time: 17:57
 */
public class KleenePlus extends KleeneN {
    public KleenePlus(Matcher<StructuredStatement> inner) {
        super(1, inner);
    }

    public KleenePlus(Matcher<StructuredStatement>... matchers) {
        super(1, matchers);
    }
}
