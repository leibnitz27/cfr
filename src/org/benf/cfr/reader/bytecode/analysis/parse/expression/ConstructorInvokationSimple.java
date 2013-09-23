package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.VarArgsRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.FunctionProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryClass;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:26
 */
public class ConstructorInvokationSimple extends AbstractConstructorInvokation implements FunctionProcessor, BoxingProcessor {

    private final ConstantPoolEntryMethodRef function;
    private final MethodPrototype methodPrototype;


    public ConstructorInvokationSimple(InferredJavaType inferredJavaType, ConstantPoolEntryMethodRef function, MethodPrototype methodPrototype, List<Expression> args) {
        super(inferredJavaType, args);
        this.function = function;
        this.methodPrototype = methodPrototype;

    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ConstructorInvokationSimple(getInferredJavaType(), function, methodPrototype, cloneHelper.replaceOrClone(getArgs()));
    }

    @Override
    public Dumper dump(Dumper d) {
        JavaTypeInstance clazz = super.getTypeInstance();
        InnerClassInfo innerClassInfo = clazz.getInnerClassHereInfo();
        List<Expression> args = getArgs();

        d.print("new ").print(clazz.toString()).print("(");
        boolean first = true;
        int start = innerClassInfo.isHideSyntheticThis() ? 1 : 0;
        for (int i = start; i < args.size(); ++i) {
            Expression arg = args.get(i);
            if (!first) d.print(", ");
            first = false;
            d.dump(arg);
        }
        d.print(")");
        return d;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof ConstructorInvokationSimple)) return false;

        return super.equals(o);
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (!(o instanceof ConstructorInvokationSimple)) return false;
        if (!super.equivalentUnder(o, constraint)) return false;
        return true;
    }

    /*
     * Duplicate code with abstractFunctionInvokation
     */
    private OverloadMethodSet getOverloadMethodSet() {
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
    public void rewriteVarArgs(VarArgsRewriter varArgsRewriter) {
        if (!methodPrototype.isVarArgs()) return;
        OverloadMethodSet overloadMethodSet = getOverloadMethodSet();
        if (overloadMethodSet == null) return;
        varArgsRewriter.rewriteVarArgsArg(overloadMethodSet, methodPrototype, getArgs());
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
            if (!overloadMethodSet.callsCorrectMethod(arg, x)) {
                /*
                 * If arg isn't the right type, shove an extra cast on the front now.
                 * Then we will forcibly remove it if we don't need it.
                 */
                JavaTypeInstance argType = overloadMethodSet.getArgType(x, arg.getInferredJavaType().getJavaTypeInstance());
                boolean ignore = false;
                if (argType instanceof JavaGenericBaseInstance) {
                    // TODO : Should check flag for ignore bad generics?
                    ignore = ((JavaGenericBaseInstance) argType).hasForeignUnbound(function.getCp());
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
            arg = boxingRewriter.sugarParameterBoxing(arg, x, overloadMethodSet);
            args.set(x, arg);
        }
        return false;
    }

}
