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
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryNameAndType;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:26
 * To change this template use File | Settings | File Templates.
 */
public class StaticFunctionInvokation extends AbstractExpression implements FunctionProcessor, BoxingProcessor {
    private final ConstantPoolEntryMethodRef function;
    private final List<Expression> args;
    private final JavaTypeInstance clazz;

    private static InferredJavaType getTypeForFunction(ConstantPoolEntryMethodRef function, List<Expression> args) {
        InferredJavaType res = new InferredJavaType(
                function.getMethodPrototype().getReturnType(function.getClassEntry().getTypeInstance(), args),
                InferredJavaType.Source.FUNCTION, true);
        return res;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new StaticFunctionInvokation(function, cloneHelper.replaceOrClone(args));
    }

    public StaticFunctionInvokation(ConstantPoolEntryMethodRef function, List<Expression> args) {
        super(getTypeForFunction(function, args));
        this.function = function;
        this.args = args;
        this.clazz = function.getClassEntry().getTypeInstance();
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        collector.collect(clazz);
        for (Expression arg : args) {
            arg.collectTypeUsages(collector);
        }
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, args.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, expressionRewriter.rewriteExpression(args.get(x), ssaIdentifiers, statementContainer, flags));
        }
        return this;
    }

    @Override
    public Dumper dump(Dumper d) {
        d.dump(clazz).print(".");
        ConstantPoolEntryNameAndType nameAndType = function.getNameAndTypeEntry();
        d.print(nameAndType.getName().getValue() + "(");
        boolean first = true;
        for (Expression arg : args) {
            if (!first) d.print(", ");
            first = false;
            d.dump(arg);
        }
        d.print(")");
        return d;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        for (Expression expression : args) {
            expression.collectUsedLValues(lValueUsageCollector);
        }
    }


    public String getName() {
        ConstantPoolEntryNameAndType nameAndType = function.getNameAndTypeEntry();
        return nameAndType.getName().getValue();
    }

    public JavaTypeInstance getClazz() {
        return clazz;
    }

    public List<Expression> getArgs() {
        return args;
    }

    public ConstantPoolEntryMethodRef getFunction() {
        return function;
    }

    @Override
    public void rewriteVarArgs(VarArgsRewriter varArgsRewriter) {
        MethodPrototype methodPrototype = function.getMethodPrototype();
        if (!methodPrototype.isVarArgs()) return;
        OverloadMethodSet overloadMethodSet = function.getOverloadMethodSet();
        if (overloadMethodSet == null) return;
        varArgsRewriter.rewriteVarArgsArg(overloadMethodSet, methodPrototype, getArgs());
    }


    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        OverloadMethodSet overloadMethodSet = function.getOverloadMethodSet();
        if (overloadMethodSet == null) {
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
            arg = boxingRewriter.rewriteExpression(arg, null, null, null);
            args.set(x, boxingRewriter.sugarParameterBoxing(arg, x, overloadMethodSet));
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof StaticFunctionInvokation)) return false;
        StaticFunctionInvokation other = (StaticFunctionInvokation) o;
        if (!clazz.equals(other.clazz)) return false;
        if (!args.equals(other.args)) return false;
        return true;
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof StaticFunctionInvokation)) return false;
        StaticFunctionInvokation other = (StaticFunctionInvokation) o;
        if (!constraint.equivalent(clazz, other.clazz)) return false;
        if (!constraint.equivalent(args, other.args)) return false;
        return true;
    }

}
