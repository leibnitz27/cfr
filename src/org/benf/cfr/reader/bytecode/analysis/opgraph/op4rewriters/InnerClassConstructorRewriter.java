package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ClassFileField;
import org.benf.cfr.reader.entities.Field;

import java.util.List;

public class InnerClassConstructorRewriter implements Op04Rewriter {
    private final ClassFile classFile;
    private final LocalVariable outerArg;
    private FieldVariable matchedField;
    private StructuredStatement assignmentStatement;

    public InnerClassConstructorRewriter(ClassFile classFile, LocalVariable outerArg) {
        this.outerArg = outerArg;
        this.classFile = classFile;
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (root == null) return;

        WildcardMatch wcm1 = new WildcardMatch();

        Matcher<StructuredStatement> m = new CollectMatch("ass1", new StructuredAssignment(wcm1.getLValueWildCard("outercopy"), new LValueExpression(outerArg)));


        /* The first usage of this variable needs to be an assignment to a final synthetic member */
        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);
        ConstructResultCollector collector = new ConstructResultCollector(wcm1);
        while (mi.hasNext()) {
            mi.advance();
            if (m.match(mi, collector)) {
                LValue lValue = wcm1.getLValueWildCard("outercopy").getMatch();
                if (lValue instanceof FieldVariable) {
                    try {
                        ClassFileField classField = classFile.getFieldByName(((FieldVariable) lValue).getFieldName());
                        Field field = classField.getField();
                        // Don't continue if it's not final + synthetic?
                        // it's a bad match, so we don't want to try for a later one.
                        if ((field.testAccessFlag(AccessFlag.ACC_SYNTHETIC) && field.testAccessFlag(AccessFlag.ACC_FINAL))) {
                            assignmentStatement = collector.assignmentStatement;
                            matchedField = (FieldVariable) lValue;
                        }
                    } catch (NoSuchFieldException e) {
                    }
                }
                return;
            }
        }
    }

    public FieldVariable getMatchedField() {
        return matchedField;
    }

    public StructuredStatement getAssignmentStatement() {
        return assignmentStatement;
    }

    private static class ConstructResultCollector extends AbstractMatchResultIterator {

        private final WildcardMatch wcm;
        private StructuredStatement assignmentStatement;

        private ConstructResultCollector(WildcardMatch wcm) {
            this.wcm = wcm;
        }

        @Override
        public void clear() {
            assignmentStatement = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            assignmentStatement = statement;
            /* We also have to rename lValue as 'this.ClassName', or simply '' in the case where there is
             * no ambiguity.
             *
             * We do this at a later step, in case there are multiple constructors.
             */
        }


        @Override
        public void collectMatches(String name, WildcardMatch wcm) {

        }
    }
}
