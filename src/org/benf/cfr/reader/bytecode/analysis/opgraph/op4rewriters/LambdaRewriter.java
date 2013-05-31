package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredStatementExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredReturn;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodHandle;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2013
 * Time: 06:26
 */
public class LambdaRewriter implements Op04Rewriter, ExpressionRewriter {

    private final ClassFile classFile;

    public LambdaRewriter(ClassFile classFile) {
        this.classFile = classFile;
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

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

    private static ConstantPoolEntryMethodHandle getHandle(Expression e) {
        if (!(e instanceof Literal)) throw new IllegalArgumentException("Expecting literal");
        TypedLiteral t = ((Literal) e).getValue();
        if (t.getType() != TypedLiteral.LiteralType.MethodHandle)
            throw new IllegalArgumentException("Expecting method handle");
        return (ConstantPoolEntryMethodHandle) t.getValue();
    }

    private static class CannotDelambaException extends IllegalStateException {
    }

    private static LocalVariable getLocalVariable(Expression e) {
        if (!(e instanceof LValueExpression)) throw new CannotDelambaException();
        LValueExpression lValueExpression = (LValueExpression) e;
        LValue lValue = lValueExpression.getLValue();
        if (!(lValue instanceof LocalVariable)) throw new CannotDelambaException();
        return (LocalVariable) lValue;
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
        ConstantPoolEntryMethodHandle targetFnHandle = getHandle(metaFactoryArgs.get(3));
        ConstantPoolEntryMethodHandle lambdaFnHandle = getHandle(metaFactoryArgs.get(4));
        MethodPrototype targetFn = targetFnHandle.getMethodRef().getMethodPrototype();
        MethodPrototype lambdaFn = lambdaFnHandle.getMethodRef().getMethodPrototype();
        String lambdaFnName = lambdaFn.getName();
        List<JavaTypeInstance> lambdaFnArgTypes = lambdaFn.getArgs();
        List<JavaTypeInstance> targetFnArgTypes = targetFn.getArgs();

        // We can't ask the prototype for instance behaviour, we have to get it from the
        // handle, as it will point to a ref.
        boolean instance = false;
        switch (lambdaFnHandle.getReferenceKind()) {
            case INVOKE_INTERFACE:
            case INVOKE_SPECIAL:
                instance = true;
                break;
        }

        if (curriedArgs.size() + targetFnArgTypes.size() - (instance ? 1 : 0) != lambdaFnArgTypes.size()) {
            throw new IllegalStateException("Bad argument counts!");
        }

        /* Now, we can call the synthetic function directly and emit it, or we could inline the synthetic, and no
         * longer emit it.
         */
        Method lambdaMethod = null;
        try {
            lambdaMethod = classFile.getMethodByPrototype(lambdaFn);
        } catch (NoSuchMethodException e) {
        }
        if (lambdaMethod == null) {
            throw new IllegalStateException("Can't find lambda target " + lambdaFn);
        }
        try {
            Op04StructuredStatement lambdaCode = lambdaMethod.getAnalysis();
            int nLambdaArgs = targetFnArgTypes.size();
            /* We will be
             * \arg0 ... arg(n-1) -> curriedArgs, arg0 ... arg(n-1)
             * where curriedArgs will lose first arg if instance method.
             */
            List<LValue> replacementParameters = ListFactory.newList();
            for (int n = instance ? 1 : 0, m = curriedArgs.size(); n < m; ++n) {
                replacementParameters.add(getLocalVariable(curriedArgs.get(n)));
            }
            List<LValue> anonymousLambdaArgs = ListFactory.newList();
            for (int n = 0; n < nLambdaArgs; ++n) {
                LocalVariable tmp = new LocalVariable("arg_" + n, new InferredJavaType(targetFnArgTypes.get(n), InferredJavaType.Source.EXPRESSION));
                anonymousLambdaArgs.add(tmp);
                replacementParameters.add(tmp);
            }
            List<LocalVariable> originalParameters = lambdaMethod.getMethodPrototype().getParameters();

            /*
             * Now we need to take the arguments for the lambda function, and replace them with names
             * in the body.
             */
            if (originalParameters.size() != replacementParameters.size()) throw new CannotDelambaException();

            Map<LValue, LValue> rewrites = MapFactory.newMap();
            for (int x = 0; x < originalParameters.size(); ++x) {
                rewrites.put(originalParameters.get(x), replacementParameters.get(x));
            }

            List<StructuredStatement> structuredLambdaStatements = MiscStatementTools.linearise(lambdaCode);
            if (structuredLambdaStatements == null) {
                throw new CannotDelambaException();
            }

            ExpressionRewriter variableRenamer = new LambdaInternalRewriter(rewrites);
            for (StructuredStatement lambdaStatement : structuredLambdaStatements) {
                lambdaStatement.rewriteExpressions(variableRenamer);
            }
            StructuredStatement lambdaStatement = lambdaCode.getStatement();
            if (structuredLambdaStatements.size() == 3 && (structuredLambdaStatements.get(1) instanceof StructuredReturn)) {
                /*
                 * it's a single element lambda expression - we can just use a statement!
                 */
                StructuredReturn structuredReturn = (StructuredReturn) structuredLambdaStatements.get(1);
                lambdaStatement = new StructuredExpressionStatement(structuredReturn.getValue(), true);
            }

            lambdaMethod.hideSynthetic();
            return new LambdaExpression(dynamicExpression.getInferredJavaType(), anonymousLambdaArgs, new StructuredStatementExpression(new InferredJavaType(lambdaMethod.getMethodPrototype().getReturnType(), InferredJavaType.Source.EXPRESSION), lambdaStatement));
        } catch (CannotDelambaException e) {
        }

        // Ok, just call the synthetic method directly.
        return new LambdaExpressionFallback(dynamicExpression.getInferredJavaType(), lambdaFnName, targetFnArgTypes, curriedArgs, instance);
    }

    public static class LambdaInternalRewriter implements ExpressionRewriter {
        private final Map<LValue, LValue> rewrites;


        public LambdaInternalRewriter(Map<LValue, LValue> rewrites) {
            this.rewrites = rewrites;
        }

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
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
            LValue replacement = rewrites.get(lValue);
            return replacement == null ? lValue : replacement;
        }

        @Override
        public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            return lValue;
        }
    }
}
