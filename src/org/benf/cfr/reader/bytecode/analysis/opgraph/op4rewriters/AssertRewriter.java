package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.InfiniteAssertRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;

import java.util.List;

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
        if (statements == null) return;
        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(statements);
        WildcardMatch wcm1 = new WildcardMatch();

        final JavaTypeInstance topClassType = classFile.getClassType();
        InnerClassInfo innerClassInfo = topClassType.getInnerClassHereInfo();
        JavaTypeInstance classType = topClassType;
        while (innerClassInfo != InnerClassInfo.NOT) {
            JavaTypeInstance nextClass = innerClassInfo.getOuterClass();
            if (nextClass == null || nextClass.equals(classType)) break;
            classType = nextClass;
            innerClassInfo = classType.getInnerClassHereInfo();
        }

        Matcher<StructuredStatement> m = new ResetAfterTest(wcm1,
                new CollectMatch("ass1", new StructuredAssignment(
                        wcm1.getStaticVariable("assertbool", topClassType, new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.TEST)),
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
                mi.rewind1();
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

    private class AssertVarCollector extends AbstractMatchResultIterator {

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
                field = classFile.getFieldByName(staticVariable.getFieldName(), staticVariable.getInferredJavaType().getJavaTypeInstance());
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
        public void collectMatches(String name, WildcardMatch wcm) {
        }

        public boolean matched() {
            return (assertField != null);
        }
    }

    private void rewriteMethods() {
        List<Method> methods = classFile.getMethods();

        WildcardMatch wcm1 = new WildcardMatch();

        Matcher<StructuredStatement> m = new ResetAfterTest(wcm1,
                new MatchOneOf(
                        new CollectMatch("ass1", new MatchSequence(
                                new StructuredIf(
                                        new BooleanOperation(
                                                new NotOperation(new BooleanExpression(new LValueExpression(assertionStatic))),
                                                wcm1.getConditionalExpressionWildcard("condition"),
                                                BoolOp.AND), null
                                ),
                                new BeginBlock(null),
                                new StructuredThrow(wcm1.getConstructorSimpleWildcard("exception", TypeConstants.ASSERTION_ERROR)),
                                new EndBlock(null)
                        )),
                        new CollectMatch("ass1b", new MatchSequence(
                                new StructuredIf(
                                        new NotOperation(
                                                new BooleanOperation(new BooleanExpression(new LValueExpression(assertionStatic)),
                                                        wcm1.getConditionalExpressionWildcard("condition"),
                                                        BoolOp.OR)), null
                                ),
                                new BeginBlock(null),
                                new StructuredThrow(wcm1.getConstructorSimpleWildcard("exception", TypeConstants.ASSERTION_ERROR)),
                                new EndBlock(null)
                        )),
                        new CollectMatch("ass2", new MatchSequence(
                                new MatchOneOf(
                                        new StructuredIf(
                                                new BooleanOperation(
                                                        new BooleanExpression(new LValueExpression(assertionStatic)),
                                                        wcm1.getConditionalExpressionWildcard("condition2"),
                                                        BoolOp.OR), null),
                                        new StructuredIf(
                                                new BooleanExpression(new LValueExpression(assertionStatic)), null)
                                ),
                                new BeginBlock(wcm1.getBlockWildcard("condBlock")),
                                new MatchOneOf(
                                        new StructuredReturn(null, null),
                                        new StructuredReturn(wcm1.getExpressionWildCard("retval"), null),
                                        new StructuredBreak(wcm1.getBlockIdentifier("breakblock"), false)
                                ),
                                new EndBlock(wcm1.getBlockWildcard("condBlock")),
                                new CollectMatch("ass2throw", new StructuredThrow(wcm1.getConstructorSimpleWildcard("exception2", TypeConstants.ASSERTION_ERROR)))
                        )),
                        new CollectMatch("assonly", new MatchSequence(
                                new StructuredIf(
                                        new NotOperation(new BooleanExpression(new LValueExpression(assertionStatic))), null
                                ),
                                new BeginBlock(null),
                                new StructuredThrow(wcm1.getConstructorSimpleWildcard("exception", TypeConstants.ASSERTION_ERROR)),
                                new EndBlock(null)
                        ))
                )
        );


        AssertUseCollector collector = new AssertUseCollector(wcm1);

        for (Method method : methods) {
            if (!method.hasCodeAttribute()) continue;

            Op04StructuredStatement top = method.getAnalysis();
            if (top == null) continue;
            /*
             * Pre-transform a couple of particularly horrible samples.
             * (where the assertion gets moved into a while block test).
             * See InfiniteAssert tests.
             */
            handleInfiniteAsserts(top);

            List<StructuredStatement> statements = MiscStatementTools.linearise(top);

            if (statements == null) continue;

            MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(statements);

            while (mi.hasNext()) {
                mi.advance();
                if (m.match(mi, collector)) {
                    mi.rewind1();
                }
            }

        }

    }

    private void handleInfiniteAsserts(Op04StructuredStatement statements) {
        InfiniteAssertRewriter rewriter = new InfiniteAssertRewriter(assertionStatic);
        rewriter.transform(statements);
    }

    private class AssertUseCollector extends AbstractMatchResultIterator {

        private StructuredStatement ass2throw;

        private final WildcardMatch wcm;

        private AssertUseCollector(WildcardMatch wcm) {
            this.wcm = wcm;
        }

        @Override
        public void clear() {
            ass2throw = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {
            if (name.equals("ass1") || name.equals("ass1b")) {
                StructuredIf ifStatement = (StructuredIf) statement;
                ConditionalExpression condition = wcm.getConditionalExpressionWildcard("condition").getMatch();
                if (name.equals("ass1")) condition = new NotOperation(condition);
                condition = condition.simplify();
                StructuredStatement structuredAssert = ifStatement.convertToAssertion(new StructuredAssert(condition));
                ifStatement.getContainer().replaceContainedStatement(structuredAssert);
            } else if (name.equals("ass2")) {
                if (ass2throw == null) throw new IllegalStateException();
                StructuredIf ifStatement = (StructuredIf) statement;
                // If there's a condition, it's in condition 2, otherwise it's an assert literal.
                WildcardMatch.ConditionalExpressionWildcard wcard = wcm.getConditionalExpressionWildcard("condition2");
                ConditionalExpression conditionalExpression = wcard.getMatch();
                if (conditionalExpression == null)
                    conditionalExpression = new BooleanExpression(new Literal(TypedLiteral.getBoolean(0)));
                // The if statement becomes an assert conditjon, the throw statement becomes the content of the if block.
                StructuredStatement structuredAssert = new StructuredAssert(conditionalExpression);
                ifStatement.getContainer().replaceContainedStatement(structuredAssert);
                ass2throw.getContainer().replaceContainedStatement(ifStatement.getIfTaken().getStatement());
            } else if (name.equals("ass2throw")) {
                ass2throw = statement;
            } else if (name.equals("assonly")) {
                StructuredIf ifStatement = (StructuredIf) statement;
                StructuredStatement structuredAssert = ifStatement.convertToAssertion(new StructuredAssert(new BooleanExpression(Literal.FALSE)));
                ifStatement.getContainer().replaceContainedStatement(structuredAssert);
            }
        }
    }

}
