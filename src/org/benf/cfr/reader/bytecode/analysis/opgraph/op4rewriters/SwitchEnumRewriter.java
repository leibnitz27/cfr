package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.*;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArrayIndex;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredSwitch;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ConstantPoolEntryClass;
import org.benf.cfr.reader.entities.ConstantPoolEntryNameAndType;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.Predicate;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 07/02/2013
 * Time: 05:49
 */
public class SwitchEnumRewriter implements Op04Rewriter {
    @Override
    public void rewrite(Op04StructuredStatement root) {


        List<StructuredStatement> structuredStatements = ListFactory.newList();
        try {
            root.linearizeStatementsInto(structuredStatements);
        } catch (UnsupportedOperationException e) {
            // Todo : Should output something at the end about this failure.
            return;
        }

        List<StructuredStatement> switchStatements = Functional.filter(structuredStatements, new Predicate<StructuredStatement>() {
            @Override
            public boolean test(StructuredStatement in) {
                return in.getClass() == StructuredSwitch.class;
            }
        });
        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        WildcardMatch wcm = new WildcardMatch();

        Matcher<StructuredStatement> m = new ResetAfterTest(wcm,
                new CollectMatch("switch", new StructuredSwitch(
                        new ArrayIndex(
                                new LValueExpression(wcm.getLValueWildCard("lut")),
                                wcm.getMemberFunction("fncall", "ordinal", new LValueExpression(wcm.getLValueWildCard("object")))),
                        null, wcm.getBlockIdentifier("block"))));


        SwitchEnumMatchResultCollector matchResultCollector = new SwitchEnumMatchResultCollector(wcm);
        while (mi.hasNext()) {
            mi.advance();
            matchResultCollector.clear();
            if (m.match(mi, matchResultCollector)) {
                tryRewrite(matchResultCollector);
            }
        }
    }


    private void tryRewrite(SwitchEnumMatchResultCollector mrc) {
        StructuredSwitch structuredSwitch = mrc.getStructuredSwitch();
        LValue lookupTable = mrc.getLookupTable();
        LValue enumObject = mrc.getEnumObject();

        if (!(lookupTable instanceof StaticVariable)) {
            return;
        }

        StaticVariable staticLookupTable = (StaticVariable) lookupTable;
        JavaTypeInstance clazz = staticLookupTable.getJavaTypeInstance();  // The inner class
        String varName = staticLookupTable.getVarName();

        /*
         * All cases will of course be integers.  The lookup table /COULD/ be perverse, but that wouldn't
         * stop it being valid for this use.... as long as the array matches the indexes.
         *
         * So here's the tricky bit - we now have to load (cached?) clazz, and find out if
         * varName was initialised like a lookup table....
         */


    }

    private static class SwitchEnumMatchResultCollector implements MatchResultCollector {

        private final WildcardMatch wcm;

        private LValue lookupTable;
        private LValue enumObject;
        private StructuredSwitch structuredSwitch;

        private SwitchEnumMatchResultCollector(WildcardMatch wcm) {
            this.wcm = wcm;
        }

        @Override
        public void clear() {
            lookupTable = null;
            enumObject = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            if (name.equals("switch")) {
                structuredSwitch = (StructuredSwitch) statement;
            }
        }

        @Override
        public void collectMatches(WildcardMatch wcm) {
            lookupTable = wcm.getLValueWildCard("lut").getMatch();
            enumObject = wcm.getLValueWildCard("object").getMatch();
        }

        public LValue getLookupTable() {
            return lookupTable;
        }

        public LValue getEnumObject() {
            return enumObject;
        }

        public StructuredSwitch getStructuredSwitch() {
            return structuredSwitch;
        }
    }
}
