package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import com.sun.tools.hat.internal.util.Misc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 28/03/2013
 * Time: 17:56
 */
public class InnerClassConstructorRewriter implements Op04Rewriter {
    private final LocalVariable outerThisArgument;

    public InnerClassConstructorRewriter(LocalVariable outerThisArgument) {
        this.outerThisArgument = outerThisArgument;
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (root == null) return;

        WildcardMatch wcm1 = new WildcardMatch();

        Matcher<StructuredStatement> m = new CollectMatch("ass1", new StructuredAssignment(wcm1.getLValueWildCard("outerthis"), new LValueExpression(outerThisArgument)));


        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);
        MatchResultCollector collector = new ConstructResultCollector(wcm1);
        while (mi.hasNext()) {
            mi.advance();
            if (m.match(mi, collector)) {
                return;
            }
        }
    }

    private static class ConstructResultCollector implements MatchResultCollector {

        private final WildcardMatch wcm;

        private ConstructResultCollector(WildcardMatch wcm) {
            this.wcm = wcm;
        }

        @Override
        public void clear() {

        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            LValue lValue = wcm.getLValueWildCard("outerthis").getMatch();
            statement.getContainer().nopOut();

        }


        @Override
        public void collectMatches(WildcardMatch wcm) {

        }
    }
}
