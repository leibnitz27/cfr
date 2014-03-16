package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.VarArgsRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.FunctionProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.state.TypeUsageCollector;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:26
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractFunctionInvokation extends AbstractExpression implements FunctionProcessor, BoxingProcessor {
    private final ConstantPoolEntryMethodRef function;
    private Expression object;
    private final List<Expression> args;
    private final ConstantPool cp;
    private final MethodPrototype methodPrototype;

    public AbstractFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, MethodPrototype methodPrototype, Expression object, List<Expression> args) {
        super(new InferredJavaType(
                methodPrototype.getReturnType(
                        object.getInferredJavaType().getJavaTypeInstance(), args
                ), InferredJavaType.Source.FUNCTION, true
        ));
        this.function = function;
        this.methodPrototype = methodPrototype;
        this.object = object;
        this.args = args;
        this.cp = cp;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        for (Expression arg : args) arg.collectTypeUsages(collector);
        methodPrototype.collectTypeUsages(collector);
        collector.collectFrom(object);
        super.collectTypeUsages(collector);
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        object = object.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, args.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        object = expressionRewriter.rewriteExpression(object, ssaIdentifiers, statementContainer, flags);
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, expressionRewriter.rewriteExpression(args.get(x), ssaIdentifiers, statementContainer, flags));
        }
        return this;
    }

    public Expression getObject() {
        return object;
    }

    public ConstantPoolEntryMethodRef getFunction() {
        return function;
    }

    public JavaTypeInstance getClassTypeInstance() {
        return function.getClassEntry().getTypeInstance();
    }

    public List<Expression> getArgs() {
        return args;
    }

    public MethodPrototype getMethodPrototype() {
        return methodPrototype;
    }

    public Expression getAppropriatelyCastArgument(int idx) {
        return methodPrototype.getAppropriatelyCastedArgument(args.get(idx), idx);
    }

    public ConstantPool getCp() {
        return cp;
    }


    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        object.collectUsedLValues(lValueUsageCollector);
        for (Expression expression : args) {
            expression.collectUsedLValues(lValueUsageCollector);
        }
    }

    private OverloadMethodSet getOverloadMethodSet() {
        OverloadMethodSet overloadMethodSet = function.getOverloadMethodSet();
        if (overloadMethodSet == null) return null;
        JavaTypeInstance objectType = object.getInferredJavaType().getJavaTypeInstance();
        if (objectType instanceof JavaGenericRefTypeInstance) {
            JavaGenericRefTypeInstance genericType = (JavaGenericRefTypeInstance) objectType;
            return overloadMethodSet.specialiseTo(genericType);
        }
        return overloadMethodSet;
    }

    public abstract String getName();

    @Override
    public void rewriteVarArgs(VarArgsRewriter varArgsRewriter) {
        if (!methodPrototype.isVarArgs()) return;
        OverloadMethodSet overloadMethodSet = getOverloadMethodSet();
        if (overloadMethodSet == null) return;
        GenericTypeBinder gtb = methodPrototype.getTypeBinderFor(args);
        varArgsRewriter.rewriteVarArgsArg(overloadMethodSet, methodPrototype, args, gtb);
    }

    @Override
    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        if (args.isEmpty()) return false;
        /*
         * Ignore completely for lambda, etc.
         */

        OverloadMethodSet overloadMethodSet = getOverloadMethodSet();
        if (overloadMethodSet == null) {
            boxingRewriter.removeRedundantCastOnly(args);
            return false;
        }

        BindingSuperContainer bindingSuperContainer = object.getInferredJavaType().getJavaTypeInstance().getBindingSupers();
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
            if (!callsCorrectEntireMethod && !overloadMethodSet.callsCorrectMethod(arg, x, gtb)) {
                /*
                 * If arg isn't the right type, shove an extra cast on the front now.
                 * Then we will forcibly remove it if we don't need it.
                 */
                JavaTypeInstance argType = overloadMethodSet.getArgType(x, arg.getInferredJavaType().getJavaTypeInstance());
                boolean ignore = false;
                if (argType instanceof JavaGenericBaseInstance) {
                    // TODO : Should check flag for ignore bad generics?
                    ignore |= ((JavaGenericBaseInstance) argType).hasForeignUnbound(cp);
                }
                /*
                 * Lambda types will always look wrong.
                 */
                if (!ignore) {
                    ignore |= arg instanceof LambdaExpression;
                    ignore |= arg instanceof LambdaExpressionFallback;
                }
                if (!ignore) {
                    arg = new CastExpression(new InferredJavaType(argType, InferredJavaType.Source.EXPRESSION, true), arg);
                }
            }

            arg = boxingRewriter.rewriteExpression(arg, null, null, null);
            arg = boxingRewriter.sugarParameterBoxing(arg, x, overloadMethodSet, gtb);
            args.set(x, arg);
        }

        return true;
    }

    @Override
    public void applyNonArgExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        object = expressionRewriter.rewriteExpression(object, ssaIdentifiers, statementContainer, flags);
    }


    /*
     * We can be SLIGHTLY clever here.  If only checked exceptions are being caught, we
     * can see if our target is declared as throwing one of these.  Otherwise, if non-checked
     * are being caught, we should always consider as throwing.
     */
    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return caught.checkAgainst(this);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof AbstractFunctionInvokation)) return false;
        AbstractFunctionInvokation other = (AbstractFunctionInvokation) o;
        if (!object.equals(other.object)) return false;
        if (!args.equals(other.args)) return false;
        return true;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof AbstractFunctionInvokation)) return false;
        AbstractFunctionInvokation other = (AbstractFunctionInvokation) o;
        if (!constraint.equivalent(object, other.object)) return false;
        if (!constraint.equivalent(args, other.args)) return false;
        return true;
    }


}
