package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssert;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredIf;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredThrow;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.Predicate;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2013
 * Time: 06:26
 */
public class AssertRewriter {

    private final ClassFile classFile;
    private ClassFileField assertionsDisabledField = null;
    private StaticVariable assertionStatic = null;

    public AssertRewriter(ClassFile classFile) {
        this.classFile = classFile;
    }

    public void sugarAsserts(Method staticInit) {
        /*
         * Determine if the static init has a line like
         *
         *         CLASSX.y = !(CLASSX.class.desiredAssertionStatus());
         *
         * where y is static final boolean.
         */
        if (!staticInit.hasCodeAttribute()) return;
        List<StructuredStatement> statements = MiscStatementTools.linearise(staticInit.getAnalysis());

        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(statements);
        WildcardMatch wcm1 = new WildcardMatch();

        JavaTypeInstance classType = classFile.getClassType();

        Matcher<StructuredStatement> m = new ResetAfterTest(wcm1,
                new CollectMatch("ass1", new StructuredAssignment(
                        wcm1.getStaticVariable("assertbool", classType, new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.TEST)),
                        new NotOperation(new BooleanExpression(
                                wcm1.getMemberFunction("assertmeth", "desiredAssertionStatus",
                                        new Literal(TypedLiteral.getClass(classType))
                                )
                        ))
                ))
        );

        AssertVarCollector matchResultCollector = new AssertVarCollector(wcm1);
        while (mi.hasNext()) {
            mi.advance();
            matchResultCollector.clear();
            if (m.match(mi, matchResultCollector)) {
                // This really should only match once.  If it matches multiple times, something else
                // is being identically initialised, which is probably wrong!
                if (matchResultCollector.matched()) break;
            }
        }
        if (!matchResultCollector.matched()) return;
        assertionsDisabledField = matchResultCollector.assertField;
        assertionStatic = matchResultCollector.assertStatic;

        /*
         * Ok, now we know what the assertion field is.  Let's search all conditionals for it - if we find one,
         * we can transform that conditional into an assert
         *
         * if (!assertionsDisabledField && (x)) --> assert(!x))
         */
        rewriteMethods();

    }

    private class AssertVarCollector implements MatchResultCollector {

        private final WildcardMatch wcm;
        ClassFileField assertField = null;
        StaticVariable assertStatic = null;

        private AssertVarCollector(WildcardMatch wcm) {
            this.wcm = wcm;
        }

        @Override
        public void clear() {
            assertField = null;
            assertStatic = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            StaticVariable staticVariable = wcm.getStaticVariable("assertbool").getMatch();

            ClassFileField field;
            try {
                field = classFile.getFieldByName(staticVariable.getVarName());
            } catch (NoSuchFieldException e) {
                return;
            }
            if (!field.getField().testAccessFlag(AccessFlag.ACC_SYNTHETIC)) return;
            assertField = field;
            statement.getContainer().nopOut();
            assertField.markHidden();
            assertStatic = staticVariable;
        }

        @Override
        public void collectMatches(WildcardMatch wcm) {

        }

        public boolean matched() {
            return (assertField != null);
        }
    }

    private void rewriteMethods() {
        List<Method> methods = classFile.getMethods();

        WildcardMatch wcm1 = new WildcardMatch();

        Matcher<StructuredStatement> m = new ResetAfterTest(wcm1,
                new MatchSequence(
                        new CollectMatch("ass1", new StructuredIf(
                                new BooleanOperation(
                                        new NotOperation(new BooleanExpression(new LValueExpression(assertionStatic))),
                                        wcm1.getConditionalExpressionWildcard("condition"),
                                        BoolOp.AND), null)),
                        new BeginBlock(),
                        new StructuredThrow(wcm1.getConstructorSimpleWildcard("exception", TypeConstants.ASSERTION_ERROR)),
                        new EndBlock()
                )
        );


        AssertUseCollector collector = new AssertUseCollector(wcm1);

        for (Method method : methods) {
            if (!method.hasCodeAttribute()) continue;

            List<StructuredStatement> statements = MiscStatementTools.linearise(method.getAnalysis());

            MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(statements);

            while (mi.hasNext()) {
                mi.advance();
                m.match(mi, collector);
            }

        }

    }

    private class AssertUseCollector implements MatchResultCollector {

        private final WildcardMatch wcm;

        private StructuredIf ifStatement;

        private AssertUseCollector(WildcardMatch wcm) {
            this.wcm = wcm;
        }

        @Override
        public void clear() {
            ifStatement = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            ifStatement = (StructuredIf) statement;
        }

        @Override
        public void collectMatches(WildcardMatch wcm) {
            ConditionalExpression condition = wcm.getConditionalExpressionWildcard("condition").getMatch();
            condition = new NotOperation(condition).simplify();
            StructuredStatement structuredAssert = ifStatement.convertToAssertion(new StructuredAssert(condition));
            if (structuredAssert == null) return;
            ifStatement.getContainer().replaceContainedStatement(structuredAssert);

        }
    }

}
