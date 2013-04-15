package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2013
 * Time: 06:26
 */
public class LambdaRewriter implements Op04Rewriter, ExpressionRewriter {
    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = ListFactory.newList();
        try {
            // This is being done multiple times, it's very inefficient!
            root.linearizeStatementsInto(structuredStatements);
        } catch (UnsupportedOperationException e) {
            // Todo : Should output something at the end about this failure.
            return;
        }

        /*
         * Lambdas come in two forms - the lambda which has been produced by the java compiler,
         * which will involve an invokedynamic call, and the lambda which has been produced by
         * an anonymous inner class - this wasn't a lambda in the original code, but we should
         * consider transforming back into lambdas because we can ;)
         */

        for (StructuredStatement statement : structuredStatements) {
            statement.rewriteExpressions(this);
        }
    }

    /*
     * Expression rewriter boilerplate - note that we can't expect ssaIdentifiers to be non-null.
     */
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof DynamicInvokation) {
            return rewriteDynamicExpression((DynamicInvokation) expression);
        }
        return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (ConditionalExpression) res;
    }

    @Override
    public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (AbstractAssignmentExpression) res;
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    @Override
    public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    /*
     * Back to the main event.
     *
     */
    private Expression rewriteDynamicExpression(DynamicInvokation dynamicExpression) {
        List<Expression> curriedArgs = dynamicExpression.getDynamicArgs();
        Expression functionCall = dynamicExpression.getInnerInvokation();
        if (functionCall instanceof StaticFunctionInvokation) {
            return rewriteDynamicExpression(dynamicExpression, (StaticFunctionInvokation) functionCall, curriedArgs);
        }
        return dynamicExpression;
    }

    private static MethodPrototype getRef(Expression e) {
        if (!(e instanceof Literal)) throw new IllegalArgumentException("Expecting literal");
        TypedLiteral t = ((Literal) e).getValue();
        if (t.getType() != TypedLiteral.LiteralType.MethodHandle)
            throw new IllegalArgumentException("Expecting method handle");
        ConstantPoolEntryMethodRef methodRef = (ConstantPoolEntryMethodRef) t.getValue();
        return methodRef.getMethodPrototype();
    }

    private Expression rewriteDynamicExpression(Expression dynamicExpression, StaticFunctionInvokation functionInvokation, List<Expression> curriedArgs) {
        String name = functionInvokation.getName();
        JavaTypeInstance typeInstance = functionInvokation.getClazz();
        if (!typeInstance.getRawName().equals("java.lang.invoke.LambdaMetafactory")) return dynamicExpression;
        if (!functionInvokation.getName().equals("metaFactory")) return dynamicExpression;

        List<Expression> metaFactoryArgs = functionInvokation.getArgs();
        if (metaFactoryArgs.size() != 6) return dynamicExpression;
        /*
         * Right, it's the 6 argument form of LambdaMetafactory.metaFactory, which we understand.
         *
         */
        MethodPrototype targetFn = getRef(metaFactoryArgs.get(3));
        MethodPrototype lambdaFn = getRef(metaFactoryArgs.get(4));
        String lambdaFnName = lambdaFn.getName();
        List<JavaTypeInstance> lambdaFnArgTypes = lambdaFn.getArgs();
        List<JavaTypeInstance> targetFnArgTypes = targetFn.getArgs();

        boolean instance = lambdaFn.isInstanceMethod();

        if (curriedArgs.size() + targetFnArgTypes.size() - (instance ? 1 : 0) != lambdaFnArgTypes.size()) {
            throw new IllegalStateException("Bad argument counts!");
        }

        return new LambdaExpression(dynamicExpression.getInferredJavaType(), lambdaFnName, targetFnArgTypes, curriedArgs, instance);
    }


}
