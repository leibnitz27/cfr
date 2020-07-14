package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.VarArgsRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.FunctionProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.GenericTypeBinder;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.annotation.Nullable;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.TypeContext;

import java.util.List;

public class StaticFunctionInvokation extends AbstractFunctionInvokation implements FunctionProcessor, BoxingProcessor {
    protected final List<Expression> args;
    private final JavaTypeInstance clazz;
    // No - a static method doesn't require an object.
    // BUT - we could be an explicit static. :(
    private Expression object;
    private @Nullable
    List<JavaTypeInstance> explicitGenerics;

    private static InferredJavaType getTypeForFunction(ConstantPoolEntryMethodRef function, List<Expression> args) {
        return new InferredJavaType(
                function.getMethodPrototype().getReturnType(function.getClassEntry().getTypeInstance(), args),
                InferredJavaType.Source.FUNCTION, true);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new StaticFunctionInvokation(getLoc(), getFunction(), cloneHelper.replaceOrClone(args), cloneHelper.replaceOrClone(object));
    }

    private StaticFunctionInvokation(BytecodeLoc loc, ConstantPoolEntryMethodRef function, List<Expression> args, Expression object) {
        super(loc, function, getTypeForFunction(function, args));
        this.args = args;
        this.clazz = function.getClassEntry().getTypeInstance();
        this.object = object;
    }

    public StaticFunctionInvokation(BytecodeLoc loc, ConstantPoolEntryMethodRef function, List<Expression> args) {
        this(loc, function, args, null);
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return BytecodeLoc.combine(this, args, object);
    }

    public void forceObject(Expression object) {
        this.object = object;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        if (object != null) {
            object.collectTypeUsages(collector);
        }
        collector.collect(clazz);
        for (Expression arg : args) {
            arg.collectTypeUsages(collector);
        }
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        LValueRewriter.Util.rewriteArgArray(lValueRewriter, ssaIdentifiers, statementContainer, args);
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        applyNonArgExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
        applyExpressionRewriterToArgs(expressionRewriter, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyBackwards(args, expressionRewriter, ssaIdentifiers, statementContainer, flags);
        applyNonArgExpressionRewriter(expressionRewriter, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public void applyExpressionRewriterToArgs(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ExpressionRewriterHelper.applyForwards(args, expressionRewriter, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public void setExplicitGenerics(List<JavaTypeInstance> types) {
        explicitGenerics = types;
    }

    @Override
    public List<JavaTypeInstance> getExplicitGenerics() {
        return explicitGenerics;
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        if (object != null) {
            d.dump(object).separator(".");
        } else {
            if (!d.getTypeUsageInformation().isStaticImport(clazz.getDeGenerifiedType(), getFixedName())) {
                d.dump(clazz, TypeContext.Static).separator(".");
            }
            if (explicitGenerics != null && !explicitGenerics.isEmpty()) {
                d.operator("<");
                boolean first = true;
                for (JavaTypeInstance typeInstance : explicitGenerics) {
                    first = StringUtils.comma(first, d);
                    d.dump(typeInstance);
                }
                d.operator(">");
            }
        }
        d.methodName(getFixedName(), getMethodPrototype(), false, false).separator("(");
        boolean first = true;
        for (Expression arg : args) {
            first = StringUtils.comma(first, d);
            d.dump(arg);
        }
        d.separator(")");
        return d;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        for (Expression expression : args) {
            expression.collectUsedLValues(lValueUsageCollector);
        }
    }

    public JavaTypeInstance getClazz() {
        return clazz;
    }

    public List<Expression> getArgs() {
        return args;
    }

    @Override
    public void rewriteVarArgs(VarArgsRewriter varArgsRewriter) {
        MethodPrototype methodPrototype = getMethodPrototype();
        if (!methodPrototype.isVarArgs()) return;
        OverloadMethodSet overloadMethodSet = methodPrototype.getOverloadMethodSet();
        if (overloadMethodSet == null) return;
        GenericTypeBinder gtb = methodPrototype.getTypeBinderFor(args);
        varArgsRewriter.rewriteVarArgsArg(overloadMethodSet, methodPrototype, getArgs(), gtb);
    }


    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        OverloadMethodSet overloadMethodSet = getMethodPrototype().getOverloadMethodSet();
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
            args.set(x, boxingRewriter.sugarParameterBoxing(arg, x, overloadMethodSet, null, getFunction().getMethodPrototype()));
        }
        return true;
    }

    @Override
    public void applyNonArgExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (object != null) {
            object = expressionRewriter.rewriteExpression(object, ssaIdentifiers, statementContainer, flags);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof StaticFunctionInvokation)) return false;
        StaticFunctionInvokation other = (StaticFunctionInvokation) o;
        if (!getName().equals(other.getName())) return false;
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
        if (!constraint.equivalent(getName(), other.getName())) return false;
        if (!constraint.equivalent(clazz, other.clazz)) return false;
        if (!constraint.equivalent(args, other.args)) return false;
        return true;
    }
}
