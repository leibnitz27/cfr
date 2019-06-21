package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.state.TypeUsageCollector;

import java.util.List;

public abstract class AbstractConstructorInvokation extends AbstractExpression implements BoxingProcessor {
    private final ConstantPoolEntryMethodRef function;
    private final MethodPrototype methodPrototype;
    private final List<Expression> args;

    AbstractConstructorInvokation(InferredJavaType inferredJavaType, ConstantPoolEntryMethodRef function, List<Expression> args) {
        super(inferredJavaType);
        this.args = args;
        this.function = function;
        this.methodPrototype = function.getMethodPrototype();
    }

    AbstractConstructorInvokation(AbstractConstructorInvokation other, CloneHelper cloneHelper) {
        super(other.getInferredJavaType());
        this.args = cloneHelper.replaceOrClone(other.args);
        this.function = other.function;
        this.methodPrototype = other.methodPrototype;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        methodPrototype.collectTypeUsages(collector);
        for (Expression arg : args) {
            arg.collectTypeUsages(collector);
        }
        super.collectTypeUsages(collector);
    }

    public List<Expression> getArgs() {
        return args;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        LValueRewriter.Util.rewriteArgArray(lValueRewriter, ssaIdentifiers, statementContainer, args);
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyForwards(args, expressionRewriter, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyBackwards(args, expressionRewriter, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    public JavaTypeInstance getTypeInstance() {
        return getInferredJavaType().getJavaTypeInstance();
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        for (Expression expression : args) {
            expression.collectUsedLValues(lValueUsageCollector);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;

        if (!(o instanceof AbstractConstructorInvokation)) return false;
        AbstractConstructorInvokation other = (AbstractConstructorInvokation) o;

        if (!getTypeInstance().equals(other.getTypeInstance())) return false;
        if (!args.equals(other.args)) return false;
        return true;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == this) return true;
        if (o == null) return false;

        if (!(o instanceof AbstractConstructorInvokation)) return false;
        AbstractConstructorInvokation other = (AbstractConstructorInvokation) o;

        if (!constraint.equivalent(getTypeInstance(), other.getTypeInstance())) return false;
        if (!constraint.equivalent(args, other.args)) return false;
        return true;
    }


    /*
     * Duplicate code with abstractFunctionInvokation
     */
    final OverloadMethodSet getOverloadMethodSet() {
        OverloadMethodSet overloadMethodSet = function.getOverloadMethodSet();
        if (overloadMethodSet == null) return null;
        JavaTypeInstance objectType = getInferredJavaType().getJavaTypeInstance();
        if (objectType instanceof JavaGenericRefTypeInstance) {
            JavaGenericRefTypeInstance genericType = (JavaGenericRefTypeInstance) objectType;
            return overloadMethodSet.specialiseTo(genericType);
        }
        return overloadMethodSet;
    }

    @Override
    public boolean isValidStatement() {
        return true;
    }

    protected final MethodPrototype getMethodPrototype() {
        return methodPrototype;
    }

    @Override
    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {

        List<Expression> args = getArgs();

        if (args.isEmpty()) return false;
        /*
         * Ignore completely for lambda, etc.
         */

        OverloadMethodSet overloadMethodSet = getOverloadMethodSet();
        if (overloadMethodSet == null) {
            /* We can't change any of the types here.
             * Best we can do is remove invalid casts.
             */
            boxingRewriter.removeRedundantCastOnly(args);
            return false;
        }

        GenericTypeBinder gtb = methodPrototype.getTypeBinderFor(args);
        boolean callsCorrectEntireMethod = overloadMethodSet.callsCorrectEntireMethod(args, gtb);
        for (int x = 0; x < args.size(); ++x) {
            /*
             * We can only remove explicit boxing if the target type is correct -
             * i.e. calling an object function with an explicit box can't have the box removed.
             *
             * This is fixed by a later pass which makes sure that the argument
             * can be passed to the target.
             */
            Expression arg = args.get(x);
            /*
             * we only need to shove a cast to the exact type on it if our current argument
             * doesn't call the 'correct' method.
             */
            if (!callsCorrectEntireMethod && !overloadMethodSet.callsCorrectMethod(arg, x, null)) {
                /*
                 * If arg isn't the right type, shove an extra cast on the front now.
                 * Then we will forcibly remove it if we don't need it.
                 */
                JavaTypeInstance argType = overloadMethodSet.getArgType(x, arg.getInferredJavaType().getJavaTypeInstance());
                boolean ignore = false;
                if (argType instanceof JavaGenericBaseInstance) {
                    // TODO : Should check flag for ignore bad generics?
                    ignore = ((JavaGenericBaseInstance) argType).hasForeignUnbound(function.getCp(), 0, false);
                }
                /*
                 * Lambda types will always look wrong.
                 */
                if (!ignore) {
                    ignore = arg instanceof LambdaExpression
                            || arg instanceof LambdaExpressionFallback;
                }
                if (!ignore) {
                    arg = new CastExpression(new InferredJavaType(argType, InferredJavaType.Source.EXPRESSION, true), arg);
                }
            }

            arg = boxingRewriter.rewriteExpression(arg, null, null, null);
            arg = boxingRewriter.sugarParameterBoxing(arg, x, overloadMethodSet, null, methodPrototype);
            args.set(x, arg);
        }
        return true;
    }

    @Override
    public void applyNonArgExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
    }
}
