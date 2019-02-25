package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.LocalDeclarationRemover;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredStatementExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredReturn;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.constantpool.*;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.lambda.LambdaUtils;

import java.util.List;
import java.util.Map;

public class LambdaRewriter implements Op04Rewriter, ExpressionRewriter {

    private final DCCommonState state;
    private final ClassFile thisClassFile;
    private final JavaTypeInstance typeInstance;
    private final Method method;

    public LambdaRewriter(DCCommonState state, Method method) {
        this.state = state;
        this.method = method;
        this.thisClassFile = method.getClassFile();
        this.typeInstance = thisClassFile.getClassType().getDeGenerifiedType();
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

    @Override
    public void handleStatement(StatementContainer statementContainer) {
    }

    /*
     * Expression rewriter boilerplate - note that we can't expect ssaIdentifiers to be non-null.
     */
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof DynamicInvokation) {
            expression = rewriteDynamicExpression((DynamicInvokation) expression);
        }
        Expression res = expression;
        if (res instanceof CastExpression) {
            Expression child = ((CastExpression) res).getChild();
            if (child instanceof LambdaExpressionCommon) {
                JavaTypeInstance resType = res.getInferredJavaType().getJavaTypeInstance();
                JavaTypeInstance childType = child.getInferredJavaType().getJavaTypeInstance();
                if (childType.implicitlyCastsTo(resType, null)) {
                    return child;
                } else {
                    /*
                     * This is more interesting - the cast doesn't work?  This means we might need to explicitly label
                     * the lambda expression type.
                     */
                    Expression tmp = new CastExpression(child.getInferredJavaType(), child, true);
                    res = new CastExpression(res.getInferredJavaType(), tmp);
                    return res;
                }
            }
        } else if (res instanceof MemberFunctionInvokation) {
            MemberFunctionInvokation invoke = (MemberFunctionInvokation)res;
            if (invoke.getObject() instanceof LambdaExpressionCommon) {
                res = invoke.withReplacedObject(new CastExpression(invoke.getObject().getInferredJavaType(), invoke.getObject()));
            }
        }
        return res;
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (ConditionalExpression) res;
    }

//    @Override
//    public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
//        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
//        return (AbstractAssignmentExpression) res;
//    }

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
            Expression res = rewriteDynamicExpression(dynamicExpression, (StaticFunctionInvokation) functionCall, curriedArgs);
            return res;
        }
        return dynamicExpression;
    }

    private static class CannotDelambaException extends IllegalStateException {
    }

    private static Expression getLambdaVariable(Expression e) {
        if (e instanceof LValueExpression) {
            LValueExpression lValueExpression = (LValueExpression) e;
            LValue lValue = lValueExpression.getLValue();
            return new LValueExpression(lValue);
        }
        if (e instanceof NewObjectArray) return e;
        throw new CannotDelambaException();
    }

    private Expression rewriteDynamicExpression(DynamicInvokation dynamicExpression, StaticFunctionInvokation functionInvokation, List<Expression> curriedArgs) {
        JavaTypeInstance typeInstance = functionInvokation.getClazz();
        if (!typeInstance.getRawName().equals(TypeConstants.lambdaMetaFactoryName)) return dynamicExpression;
        String functionName = functionInvokation.getName();

        DynamicInvokeType dynamicInvokeType = DynamicInvokeType.lookup(functionName);
        if (dynamicInvokeType == DynamicInvokeType.UNKNOWN) return dynamicExpression;

        List<Expression> metaFactoryArgs = functionInvokation.getArgs();
        if (metaFactoryArgs.size() != 6) return dynamicExpression;
        /*
         * Right, it's the 6 argument form of LambdaMetafactory.metaFactory, which we understand.
         *
         */
        Expression arg = metaFactoryArgs.get(3);

        List<JavaTypeInstance> targetFnArgTypes = LambdaUtils.getLiteralProto(arg).getArgs();

        ConstantPoolEntryMethodHandle lambdaFnHandle = LambdaUtils.getHandle(metaFactoryArgs.get(4));
        ConstantPoolEntryMethodRef lambdaMethRef = lambdaFnHandle.getMethodRef();
        JavaTypeInstance lambdaTypeLocation = lambdaMethRef.getClassEntry().getTypeInstance();
        MethodPrototype lambdaFn = lambdaMethRef.getMethodPrototype();
        String lambdaFnName = lambdaFn.getName();
        List<JavaTypeInstance> lambdaFnArgTypes = lambdaFn.getArgs();

        if (!(lambdaTypeLocation instanceof JavaRefTypeInstance)) {
            return dynamicExpression;
        }
        JavaRefTypeInstance lambdaTypeRefLocation = (JavaRefTypeInstance) lambdaTypeLocation;
        ClassFile classFile = null;
        if (this.typeInstance.equals(lambdaTypeRefLocation)) {
            classFile = thisClassFile;
        } else {
            try {
                classFile = state.getClassFile(lambdaTypeRefLocation);
            } catch (CannotLoadClassException ignore) {
                // We can't load the lambda target - we can't really make any assumptions about what it will do.
            }
        }

        // We can't ask the prototype for instance behaviour, we have to get it from the
        // handle, as it will point to a ref.
        boolean instance = false;
        switch (lambdaFnHandle.getReferenceKind()) {
            case INVOKE_INTERFACE:
            case INVOKE_SPECIAL:
            case INVOKE_VIRTUAL:
                instance = true;
                break;
        }

        /*
         * If we don't have the classfile (let's say we're looking at java8's consumer in java6) we can still GUESS
         * what it was going to do....
         */
        if (classFile == null) {
            return new LambdaExpressionFallback(lambdaTypeRefLocation, dynamicExpression.getInferredJavaType(), lambdaFnName, targetFnArgTypes, curriedArgs, instance);
        }

        if (curriedArgs.size() + targetFnArgTypes.size() - (instance ? 1 : 0) != lambdaFnArgTypes.size()) {
            throw new IllegalStateException("Bad argument counts!");
        }

        /* Now, we can call the synthetic function directly and emit it, or we could inline the synthetic, and no
         * longer emit it.
         */
        Method lambdaMethod;
        try {
            lambdaMethod = classFile.getMethodByPrototype(lambdaFn);
        } catch (NoSuchMethodException ignore) {
            // This might happen if you're using a JRE which doesn't have support classes, etc.
            return dynamicExpression;
        }
        for (int x = 0, len = curriedArgs.size(); x < len; ++x) {
            /*
             * If a curried arg is a supplier, and not an LValue, then there needs to be an explicit cast in place.
             *
             */
            Expression curriedArg = curriedArgs.get(x);
            JavaTypeInstance curriedArgType = curriedArg.getInferredJavaType().getJavaTypeInstance();
            if (curriedArgType.getDeGenerifiedType().equals(TypeConstants.SUPPLIER)) {
                if (curriedArg instanceof CastExpression) {
                    CastExpression castExpression = (CastExpression)curriedArg;
                    curriedArg = new CastExpression(curriedArg.getInferredJavaType(), castExpression.getChild(), true);
                } else if (!(curriedArg instanceof LValueExpression)) {
                    curriedArg = new CastExpression(curriedArg.getInferredJavaType(), curriedArg, true);
                }
            }
            curriedArgs.set(x, CastExpression.removeImplicit(curriedArg));
        }
        if (this.typeInstance.equals(lambdaTypeRefLocation) && lambdaMethod.testAccessFlag(AccessFlagMethod.ACC_SYNTHETIC)) {
            try {
                /*
                 * This is a local synthetic lambda - we'll try to inline it.
                 */
                Op04StructuredStatement lambdaCode = lambdaMethod.getAnalysis();
                int nLambdaArgs = targetFnArgTypes.size();
                /* We will be
                 * \arg0 ... arg(n-1) -> curriedArgs, arg0 ... arg(n-1)
                 * where curriedArgs will lose first arg if instance method.
                 */
                List<Expression> replacementParameters = ListFactory.newList();
                for (int n = instance ? 1 : 0, m = curriedArgs.size(); n < m; ++n) {
                    replacementParameters.add(getLambdaVariable(curriedArgs.get(n)));
                }
                List<LValue> anonymousLambdaArgs = ListFactory.newList();
                List<LocalVariable> originalParameters = lambdaMethod.getMethodPrototype().getComputedParameters();
                int offset = replacementParameters.size();
                for (int n = 0; n < nLambdaArgs; ++n) {
                    LocalVariable original = originalParameters.get(n + offset);
                    String name = original.getName().getStringName();
                    LocalVariable tmp = new LocalVariable(name, new InferredJavaType(targetFnArgTypes.get(n), InferredJavaType.Source.EXPRESSION));
                    anonymousLambdaArgs.add(tmp);
                    replacementParameters.add(new LValueExpression(tmp));
                }
                // getParameters(lambdaMethod.getConstructorFlag());

                /*
                 * Now we need to take the arguments for the lambda function, and replace them with names
                 * in the body.
                 */
                if (originalParameters.size() != replacementParameters.size()) throw new CannotDelambaException();

                Map<LValue, Expression> rewrites = MapFactory.newMap();
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

                /*
                 * Any method scoped classes that were being used in the lambda method now belong to me.
                 * (maniac laughter).
                 */
                //noinspection unused
                boolean copied = method.copyLocalClassesFrom(lambdaMethod);
                Op04StructuredStatement placeHolder = new Op04StructuredStatement(lambdaStatement);

                /*
                 * Need to strip out declarations, as we will re-examine scope.
                 * This is horrid, but necessary to deal with local classes defined inside lambda expressions.
                 */
                StructuredScope scope = new StructuredScope();
                placeHolder.transform(new LocalDeclarationRemover(), scope);

                return new LambdaExpression(dynamicExpression.getInferredJavaType(), anonymousLambdaArgs, new StructuredStatementExpression(new InferredJavaType(lambdaMethod.getMethodPrototype().getReturnType(), InferredJavaType.Source.EXPRESSION), lambdaStatement));
            } catch (CannotDelambaException ignore) {
            }
        }

        // Ok, just call the synthetic method directly.
        return new LambdaExpressionFallback(lambdaTypeRefLocation, dynamicExpression.getInferredJavaType(), lambdaFnName, targetFnArgTypes, curriedArgs, instance);
    }

    public static class LambdaInternalRewriter implements ExpressionRewriter {
        private final Map<LValue, Expression> rewrites;

        LambdaInternalRewriter(Map<LValue, Expression> rewrites) {
            this.rewrites = rewrites;
        }

        @Override
        public void handleStatement(StatementContainer statementContainer) {

        }

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (expression instanceof LValueExpression) {
                LValue lv = ((LValueExpression) expression).getLValue();
                Expression rewrite = rewrites.get(lv);
                if (rewrite != null) return rewrite;
            }
            return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        }

        @Override
        public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
            return (ConditionalExpression) res;
        }

//        @Override
//        public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
//            Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
//            return (AbstractAssignmentExpression) res;
//        }

        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            Expression replacement = rewrites.get(lValue);
            if (replacement instanceof LValueExpression) {
                return ((LValueExpression) replacement).getLValue();
            }
            return lValue;
        }

        @Override
        public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            return lValue;
        }
    }
}
