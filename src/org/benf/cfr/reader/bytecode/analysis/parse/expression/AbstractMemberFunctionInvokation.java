package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.VarArgsRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.FunctionProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.state.TypeUsageCollector;

import java.util.List;

/*
 * Lot of common code in here and static function invokation.
 */
public abstract class AbstractMemberFunctionInvokation extends AbstractFunctionInvokation implements FunctionProcessor, BoxingProcessor {
    private final ConstantPool cp;
    private final List<Expression> args;
    private Expression object;
    private final List<Boolean> nulls;

    public AbstractMemberFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, Expression object, List<Expression> args, List<Boolean> nulls) {
        super(function,
                new InferredJavaType(
                function.getMethodPrototype().getReturnType(
                        object.getInferredJavaType().getJavaTypeInstance(), args
                ), InferredJavaType.Source.FUNCTION, true
        ));
        this.object = object;
        this.args = args;
        this.nulls = nulls;
        this.cp = cp;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        for (Expression arg : args) arg.collectTypeUsages(collector);
        getMethodPrototype().collectTypeUsages(collector);
        collector.collectFrom(object);
        super.collectTypeUsages(collector);
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        for (int x = args.size()-1; x >=0; --x) {
            args.set(x, args.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        object = object.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        object = expressionRewriter.rewriteExpression(object, ssaIdentifiers, statementContainer, flags);
        applyExpressionRewriterToArgs(expressionRewriter, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyForwards(args, expressionRewriter, ssaIdentifiers, statementContainer, flags);
        object = expressionRewriter.rewriteExpression(object, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public void applyExpressionRewriterToArgs(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyForwards(args, expressionRewriter, ssaIdentifiers, statementContainer, flags);
    }

    // Ignored, for now.
    @Override
    public void setExplicitGenerics(List<JavaTypeInstance> types) {
    }

    @Override
    public List<JavaTypeInstance> getExplicitGenerics() {
        return null;
    }

    public Expression getObject() {
        return object;
    }

    public JavaTypeInstance getClassTypeInstance() {
        return getFunction().getClassEntry().getTypeInstance();
    }

    public List<Expression> getArgs() {
        return args;
    }

    public List<Boolean> getNulls() {
        return nulls;
    }

    public Expression getAppropriatelyCastArgument(int idx) {
        return getMethodPrototype().getAppropriatelyCastedArgument(args.get(idx), idx);
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
        OverloadMethodSet overloadMethodSet = getFunction().getOverloadMethodSet();
        if (overloadMethodSet == null) return null;
        JavaTypeInstance objectType = object.getInferredJavaType().getJavaTypeInstance();
        if (objectType instanceof JavaGenericRefTypeInstance) {
            JavaGenericRefTypeInstance genericType = (JavaGenericRefTypeInstance) objectType;
            return overloadMethodSet.specialiseTo(genericType);
        }
        return overloadMethodSet;
    }

    @Override
    public void rewriteVarArgs(VarArgsRewriter varArgsRewriter) {
        MethodPrototype methodPrototype = getMethodPrototype();
        if (!methodPrototype.isVarArgs()) return;
        OverloadMethodSet overloadMethodSet = getOverloadMethodSet();
        if (overloadMethodSet == null) return;
        GenericTypeBinder gtb = methodPrototype.getTypeBinderFor(args);
        varArgsRewriter.rewriteVarArgsArg(overloadMethodSet, methodPrototype, args, gtb);
    }


    private Expression insertCastOrIgnore(Expression arg, OverloadMethodSet overloadMethodSet, int x) {
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
        return arg;
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

        MethodPrototype methodPrototype = getMethodPrototype();
        BindingSuperContainer bindingSuperContainer = object.getInferredJavaType().getJavaTypeInstance().getBindingSupers();
        GenericTypeBinder gtb = methodPrototype.getTypeBinderFor(args);

        boolean callsCorrectEntireMethod = overloadMethodSet.callsCorrectEntireMethod(args, gtb);
        boolean nullsPresent = false;
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
                arg = insertCastOrIgnore(arg, overloadMethodSet, x);
            }

            arg = boxingRewriter.rewriteExpression(arg, null, null, null);
            arg = boxingRewriter.sugarParameterBoxing(arg, x, overloadMethodSet, gtb, methodPrototype);
            nullsPresent |= (Literal.NULL.equals(arg));
            args.set(x, arg);
        }
        if (nullsPresent) {
            callsCorrectEntireMethod = overloadMethodSet.callsCorrectEntireMethod(args, gtb);
            if (!callsCorrectEntireMethod) {
                for (int x = 0; x < args.size(); ++x) {
                    Expression arg = args.get(x);
                    if (Literal.NULL.equals(arg)) {
                        arg = insertCastOrIgnore(arg, overloadMethodSet, x);
                        args.set(x, arg);
                    }
                }
            }
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
        if (!(o instanceof AbstractMemberFunctionInvokation)) return false;
        AbstractMemberFunctionInvokation other = (AbstractMemberFunctionInvokation) o;
        if (!object.equals(other.object)) return false;
        if (!args.equals(other.args)) return false;
        return true;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof AbstractMemberFunctionInvokation)) return false;
        AbstractMemberFunctionInvokation other = (AbstractMemberFunctionInvokation) o;
        if (!constraint.equivalent(object, other.object)) return false;
        if (!constraint.equivalent(args, other.args)) return false;
        return true;
    }


}
