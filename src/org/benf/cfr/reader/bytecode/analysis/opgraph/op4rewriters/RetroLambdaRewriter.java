package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.EmptyMatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchOneOf;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchSequence;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.Matcher;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.ExpressionRewriterTransformer;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LambdaExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LambdaExpressionFallback;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StaticFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredStatementExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredReturn;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.AccessFlagMethod;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ClassFileField;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.attributes.AttributeCode;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.MiscUtils;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.functors.Predicate;

import java.util.List;

/*
 * It's totally unreasonable to scan everything for possible things that MIGHT LOOK LIKE RETROLAMBDA.
 * but we can look for a few simple patterns.
 *
 * 1) A calls B::lambdaFactory(A)
 * 2a) which returns instance of B that captures the A,
 * 2b) which implements interface I1,
 * 2c) which is a functional interface (1 method).
 * 2d) and that method is marked with LambdaForm.Hidden, or is an override.
 * 3) B's implementation of I1 calls a synthetic method in A, passing the captured A as the first argument.
 * 4) That synthetic method calls a real method in A.
 * 5) that real method (if synthetic) does some work!
 * (phew).
 */
public class RetroLambdaRewriter extends AbstractExpressionRewriter {
    private final DCCommonState state;
    private final ClassFile mainClazz;

    private static final String lambdaFactoryLabel = "lambdaFactory$";
    private static final String getLambdaName = "get$lambda";

    public RetroLambdaRewriter(DCCommonState state, ClassFile mainClazz) {
        this.state = state;
        this.mainClazz = mainClazz;
    }

    public static void rewrite(ClassFile classFile, DCCommonState state) {
        for (Method m : classFile.getMethods()) {
            rewrite(m, classFile, state);
        }
    }

    private static void rewrite(Method m, ClassFile classFile, DCCommonState state) {
        AttributeCode code = m.getCodeAttribute();
        if (code == null) return;
        Op04StructuredStatement analysis = code.analyse();
        new ExpressionRewriterTransformer(new RetroLambdaRewriter(state, classFile)).transform(analysis);
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof StaticFunctionInvokation) {
            Expression replacement = considerCandidateInvokation((StaticFunctionInvokation) expression);
            if (replacement != null) return replacement;
        }
        return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
    }

    private Expression considerCandidateInvokation(StaticFunctionInvokation sf) {
        String name = sf.getMethodPrototype().getName();
        if (!(name.equals(lambdaFactoryLabel) || (name.equals(getLambdaName)))) return null;
        List<Expression> sfArgs = sf.getArgs();
        if (sfArgs.size() != 1) return null;
        Expression sfArg = sfArgs.get(0);
        if (!MiscUtils.isThis(sfArg, mainClazz.getClassType())) return null;

        ClassFile cf = state.getClassFile(sf.getClazz());
        Method lamMeth;
        try {
            List<Method> m;
            m = cf.getMethodByName(name);
            if (m.size() != 1) return null;
            lamMeth = m.get(0);
        } catch (NoSuchMethodException e) {
            return null;
        }

        Op04StructuredStatement candidate = lamMeth.getAnalysis();
        if (candidate == null) return null;

        List<LocalVariable> args = lamMeth.getMethodPrototype().getComputedParameters();
        if (args.size() != 1) return null;
        LocalVariable bound = args.get(0);

        WildcardMatch wcm = new WildcardMatch();
        Matcher<StructuredStatement> matcher = new MatchSequence(
                new BeginBlock(null),
                new StructuredReturn(BytecodeLoc.NONE, wcm.getConstructorSimpleWildcard("constr", sf.getClazz()), sf.getClazz()),
                new EndBlock(null)
        );

        List<StructuredStatement> stm = ListFactory.newList();
        candidate.linearizeStatementsInto(stm);
        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(stm);
        mi.advance();
        if (!matcher.match(mi, new EmptyMatchResultCollector())) return null;

        ConstructorInvokationSimple constr = wcm.getConstructorSimpleWildcard("constr").getMatch();
        List<Expression> constrArgs = constr.getArgs();
        if (constrArgs.size() != 1) return null;
        Expression constrArg = constrArgs.get(0);
        if (!new LValueExpression(bound).equals(constrArg)) return null;
        List<ClassFileField> fields = cf.getFields();
        if (fields.size() != 1) return null;
        // 2a satisfied!

        JavaTypeInstance returnType = sf.getMethodPrototype().getReturnType();
        if (!sf.getClazz().getBindingSupers().containsBase(returnType)) return null;

        ClassFile cfReturn;
        try {
            cfReturn = state.getClassFile(returnType);
            if (!cfReturn.isInterface()) return null;
            List<Method> interfaceMethods = cfReturn.getMethods();
            if (interfaceMethods.size() != 1) return null;
        } catch (CannotLoadClassException e) {
            // If we can't find the interface, then is it a final class, with ONE method?
            // We'll test the ONE METHOD below.
            // TODO: Also require tolerant flag?
            if (!cf.testAccessFlag(AccessFlag.ACC_FINAL)) return null;
        }
        // 2b satisfied.

        List<Method> methods = Functional.filter(cf.getMethods(), new Predicate<Method>() {
            @Override
            public boolean test(Method in) {
                return !(in.testAccessFlag(AccessFlagMethod.ACC_STATIC) || in.isConstructor());
            }
        });
        if (methods.size() != 1) return null;
        // 2c satisfied.

        // 2d - check for LambdaForm.Hidden (or override). (TODO)

        // this method should call a single thing in the original class, passing arguments verbatim, prefixed with
        // the member.
        Method method = methods.get(0);
        StaticFunctionInvokation m2callReal = check3(method);
        if (m2callReal == null) return null;
        // 3 satisfied!

        /*
         * And the function called by this, must be unambiguously a synthetic forwarder to a real function.
         *
         * At this point, we have a choice - we can either directly call the lambda indirect, as we've successfully
         * cut the temporary class out of the picture.  OR, we can see if we can unwrap things one level further.
         */
        Method mainLambdaIndirect = getMainLambdaIndirect(m2callReal);
        if (mainLambdaIndirect == null) return null;

        Expression fullLambdaBody = getFullLambdaBody(mainLambdaIndirect);
        method.hideSynthetic();
        // By this point we're ok with hiding the synthetic class.
        cf.markHiddenInnerClass();
        if (fullLambdaBody == null) {
            List<Expression> curried = ListFactory.newList();
            curried.add(sfArg);
            InferredJavaType ijt = new InferredJavaType(mainLambdaIndirect.getMethodPrototype().getReturnType(), InferredJavaType.Source.TRANSFORM);
            // We can only give a simple lambda which calls the method the external class calls.
            return new LambdaExpressionFallback(BytecodeLoc.NONE, mainClazz.getClassType(), ijt , mainLambdaIndirect.getMethodPrototype(), mainLambdaIndirect.getMethodPrototype().getArgs(), curried, false);
        }

        mainLambdaIndirect.hideSynthetic();
        return fullLambdaBody;
    }

    private StaticFunctionInvokation check3(Method method) {
        WildcardMatch wcm = new WildcardMatch();
        Op04StructuredStatement m2code = method.getAnalysis();
        if (m2code == null) return null;

        List<LocalVariable> m2params = method.getMethodPrototype().getComputedParameters();
        List<Expression> m3args = ListFactory.newList();
        m3args.add(wcm.getExpressionWildCard("field"));
        for (LocalVariable lv : m2params) {
            m3args.add(new LValueExpression(lv));
        }
        WildcardMatch.StaticFunctionInvokationWildcard m2call = wcm.getStaticFunction("m2call", mainClazz.getClassType(), method.getMethodPrototype().getReturnType(), null, m3args);
        Matcher<StructuredStatement> m2matcher = new MatchSequence(
                new BeginBlock(null),
                new MatchOneOf(
                        new StructuredExpressionStatement(BytecodeLoc.NONE, m2call, false),
                        new StructuredReturn(BytecodeLoc.NONE, m2call, method.getMethodPrototype().getReturnType())
                ),
                new EndBlock(null)
        );

        List<StructuredStatement> stm2 = ListFactory.newList();
        m2code.linearizeStatementsInto(stm2);
        MatchIterator<StructuredStatement> mi2 = new MatchIterator<StructuredStatement>(stm2);
        mi2.advance();
        if (!m2matcher.match(mi2, new EmptyMatchResultCollector())) return null;
        return wcm.getStaticFunction("m2call").getMatch();
    }

    private Expression getFullLambdaBody(Method mainLambdaIndirect) {
        List<StructuredStatement> stmStaticLocal = ListFactory.newList();
        Op04StructuredStatement lambdaStaticLocal = mainLambdaIndirect.getAnalysis();
        if (lambdaStaticLocal == null) return null;
        lambdaStaticLocal.linearizeStatementsInto(stmStaticLocal);

        WildcardMatch wcm = new WildcardMatch();
        List<LocalVariable> m2params = mainLambdaIndirect.getMethodPrototype().getComputedParameters();
        List<Expression> m3args = ListFactory.newList();
        if (m2params.size() == 0) return null;
        Expression thisPtr = new LValueExpression(m2params.get(0));
        if (!thisPtr.getInferredJavaType().getJavaTypeInstance().equals(mainLambdaIndirect.getClassFile().getClassType())) return null;

        for (int x = 1; x < m2params.size(); ++x) {
            m3args.add(new LValueExpression(m2params.get(x)));
        }
        WildcardMatch.MemberFunctionInvokationWildcard m2call = wcm.getMemberFunction("indirect", null, false, thisPtr, m3args);
        Matcher<StructuredStatement> m2matcher = new MatchSequence(
                new BeginBlock(null),
                new MatchOneOf(
                        new StructuredExpressionStatement(BytecodeLoc.NONE, m2call, false),
                        new StructuredReturn(BytecodeLoc.NONE, m2call, mainLambdaIndirect.getMethodPrototype().getReturnType())
                ),
                new EndBlock(null)
        );
        MatchIterator<StructuredStatement> mi2 = new MatchIterator<StructuredStatement>(stmStaticLocal);
        mi2.advance();
        if (!m2matcher.match(mi2, new EmptyMatchResultCollector())) return null;

        MemberFunctionInvokation member = wcm.getMemberFunction("indirect").getMatch();
        if (!member.getMethodPrototype().getClassType().equals(mainClazz.getClassType())) return null;

        Method lambdaBody;
        try {
            List<Method> methods = mainClazz.getMethodByName(member.getName());
            if (methods.size() != 1) return null;
            lambdaBody = methods.get(0);
        } catch (NoSuchMethodException e) {
            return null;
        }

        Op04StructuredStatement body = lambdaBody.getAnalysis();
        if (body == null) return null;

        List<LocalVariable> bodyArgs = lambdaBody.getMethodPrototype().getComputedParameters();
        List<LValue> bodyLV = ListFactory.newList();
        List<JavaTypeInstance> bodyTypes = ListFactory.newList();
        for (LocalVariable arg : bodyArgs) {
            bodyLV.add(arg);
            bodyTypes.add(arg.getInferredJavaType().getJavaTypeInstance());
        }

        lambdaBody.hideSynthetic();
        return new LambdaExpression(BytecodeLoc.NONE, new InferredJavaType(member.getMethodPrototype().getReturnType(), InferredJavaType.Source.TRANSFORM), bodyLV, bodyTypes, new StructuredStatementExpression( new InferredJavaType(member.getMethodPrototype().getReturnType(), InferredJavaType.Source.TRANSFORM), body.getStatement()));
    }

    private Method getMainLambdaIndirect(StaticFunctionInvokation m2callReal) {
        Method mainLambdaIndirect;
        try {
            List<Method> indirects = mainClazz.getMethodByName(m2callReal.getName());
            if (indirects.size() != 1) return null;
            mainLambdaIndirect = indirects.get(0);
        } catch (NoSuchMethodException e) {
            return null;
        }
        if (!mainLambdaIndirect.testAccessFlag(AccessFlagMethod.ACC_STATIC)) return null;
        return mainLambdaIndirect;
    }
}
