package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.CollectMatch;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.Matcher;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.SuperFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 28/03/2013
 * Time: 17:56
 */
public class RedundantSuperRewriter implements Op04Rewriter {

    public RedundantSuperRewriter() {
    }

    protected List<Expression> getSuperArgs(WildcardMatch wcm) {
        return null;
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        WildcardMatch wcm1 = new WildcardMatch();

        Matcher<StructuredStatement> m = new CollectMatch("ass1", new StructuredExpressionStatement(wcm1.getSuperFunction("s1", getSuperArgs(wcm1)), false));


        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);
        MatchResultCollector collector = new SuperResultCollector(wcm1);
        while (mi.hasNext()) {
            mi.advance();
            if (m.match(mi, collector)) {
                return;
            }
        }
    }

    protected boolean canBeNopped(SuperFunctionInvokation superInvokation) {
        // Does the super function have no arguments, after we ignore synthetic parameters?
        return superInvokation.isEmptyIgnoringSynthetics();
    }

    private class SuperResultCollector implements MatchResultCollector {

        private final WildcardMatch wcm;

        private SuperResultCollector(WildcardMatch wcm) {
            this.wcm = wcm;
        }

        @Override
        public void clear() {

        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            SuperFunctionInvokation superInvokation = wcm.getSuperFunction("s1").getMatch();

            if (canBeNopped(superInvokation)) {
                statement.getContainer().nopOut();
            }

        }


        @Override
        public void collectMatches(WildcardMatch wcm) {

        }
    }
}
