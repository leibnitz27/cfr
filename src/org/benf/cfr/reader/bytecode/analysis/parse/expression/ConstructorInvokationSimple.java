package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.VarArgsRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.FunctionProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.SentinelLocalClassLValue;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class ConstructorInvokationSimple extends AbstractConstructorInvokation implements FunctionProcessor {

    private final MemberFunctionInvokation constructorInvokation;

    public ConstructorInvokationSimple(MemberFunctionInvokation constructorInvokation,
                                       InferredJavaType inferredJavaType, List<Expression> args) {
        super(inferredJavaType, constructorInvokation.getFunction(), args);
        this.constructorInvokation = constructorInvokation;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ConstructorInvokationSimple(constructorInvokation, getInferredJavaType(), cloneHelper.replaceOrClone(getArgs()));
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }


    @Override
    public Dumper dumpInner(Dumper d) {
        JavaTypeInstance clazz = super.getTypeInstance();
        List<Expression> args = getArgs();
        MethodPrototype prototype = constructorInvokation.getMethodPrototype();

        d.print("new ").dump(clazz).print("(");
        boolean first = true;
        for (int i = 0; i < args.size(); ++i) {
            if (prototype.isHiddenArg(i)) continue;
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
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        JavaTypeInstance lValueType = constructorInvokation.getClassTypeInstance();
        InnerClassInfo innerClassInfo = lValueType.getInnerClassHereInfo();

        if (innerClassInfo.isMethodScopedClass() && !innerClassInfo.isAnonymousClass()) {
            lValueUsageCollector.collect(new SentinelLocalClassLValue(lValueType));
        }

        super.collectUsedLValues(lValueUsageCollector);
    }


    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (!(o instanceof ConstructorInvokationSimple)) return false;
        if (!super.equivalentUnder(o, constraint)) return false;
        return true;
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return caught.checkAgainst(constructorInvokation);
    }

    @Override
    public void rewriteVarArgs(VarArgsRewriter varArgsRewriter) {
        MethodPrototype methodPrototype = getMethodPrototype();
        if (!methodPrototype.isVarArgs()) return;
        OverloadMethodSet overloadMethodSet = getOverloadMethodSet();
        if (overloadMethodSet == null) return;
        GenericTypeBinder gtb = methodPrototype.getTypeBinderFor(getArgs());
        varArgsRewriter.rewriteVarArgsArg(overloadMethodSet, methodPrototype, getArgs(), gtb);
    }


}
