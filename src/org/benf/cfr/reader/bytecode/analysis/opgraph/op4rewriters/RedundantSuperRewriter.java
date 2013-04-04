package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.CollectMatch;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.Matcher;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.SuperFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
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

    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = ListFactory.newList();
        try {
            root.linearizeStatementsInto(structuredStatements);
        } catch (UnsupportedOperationException e) {
            // Todo : Should output something at the end about this failure.
            return;
        }

        WildcardMatch wcm1 = new WildcardMatch();

        Matcher<StructuredStatement> m = new CollectMatch("ass1", new StructuredExpressionStatement(wcm1.getSuperFunction("s1")));


        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);
        MatchResultCollector collector = new SuperResultCollector(wcm1);
        while (mi.hasNext()) {
            mi.advance();
            if (m.match(mi, collector)) {
                return;
            }
        }
    }

    private static class SuperResultCollector implements MatchResultCollector {

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

            // Does the super function have no arguments, after we ignore synthetic parameters?
            if (superInvokation.isEmptyIgnoringSynthetics()) {
                statement.getContainer().nopOut();
            }

        }


        @Override
        public void collectMatches(WildcardMatch wcm) {

        }
    }
}
