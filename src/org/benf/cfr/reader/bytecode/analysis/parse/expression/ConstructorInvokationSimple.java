package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.VarArgsRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.FunctionProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.SentinelLocalClassLValue;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFileField;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class ConstructorInvokationSimple extends AbstractConstructorInvokation implements FunctionProcessor {

    private final MemberFunctionInvokation constructorInvokation;
    private InferredJavaType constructionType;

    public ConstructorInvokationSimple(MemberFunctionInvokation constructorInvokation,
                                       InferredJavaType inferredJavaType, InferredJavaType constructionType,
                                       List<Expression> args) {
        super(inferredJavaType, constructorInvokation.getFunction(), args);
        this.constructorInvokation = constructorInvokation;
        this.constructionType = constructionType;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ConstructorInvokationSimple(constructorInvokation, getInferredJavaType(), constructionType, cloneHelper.replaceOrClone(getArgs()));
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    private JavaTypeInstance getFinalDisplayTypeInstance() {
        JavaTypeInstance res = constructionType.getJavaTypeInstance();
        if (!(res instanceof JavaGenericBaseInstance)) return res;
        if (!((JavaGenericBaseInstance) res).hasL01Wildcard()) return res;
        res = ((JavaGenericBaseInstance) res).getWithoutL01Wildcard();
        return res;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        JavaTypeInstance clazz = getFinalDisplayTypeInstance();
        List<Expression> args = getArgs();
        MethodPrototype prototype = constructorInvokation.getMethodPrototype();

        if (prototype.isInnerOuterThis() && prototype.isHiddenArg(0) && args.size() > 0) {
            Expression a1 = args.get(0);

            test : if (!a1.toString().equals(MiscConstants.THIS)) {
                if (a1 instanceof LValueExpression) {
                    LValue lValue = ((LValueExpression) a1).getLValue();
                    if (lValue instanceof FieldVariable) {
                        ClassFileField classFileField = ((FieldVariable) lValue).getClassFileField();
                        if (classFileField.isSyntheticOuterRef()) break test;
                    }
                }
                d.dump(a1).print('.');
            }
        }

        d.keyword("new ").dump(clazz).separator("(");
        boolean first = true;
        for (int i = 0; i < args.size(); ++i) {
            if (prototype.isHiddenArg(i)) continue;
            Expression arg = args.get(i);
            if (!first) d.print(", ");
            first = false;
            d.dump(arg);
        }
        d.separator(")");
        return d;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof ConstructorInvokationSimple)) return false;

        return super.equals(o);
    }

    public static boolean isAnonymousMethodType(JavaTypeInstance lValueType) {
        InnerClassInfo innerClassInfo = lValueType.getInnerClassHereInfo();
        if (innerClassInfo.isMethodScopedClass() && !innerClassInfo.isAnonymousClass()) {
            return true;
        }
        return false;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        JavaTypeInstance lValueType = constructorInvokation.getClassTypeInstance();
        if (isAnonymousMethodType(lValueType)) {
            lValueUsageCollector.collect(new SentinelLocalClassLValue(lValueType), ReadWrite.READ /* not strictly.. */);
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

    public MethodPrototype getConstructorPrototype() {
        return getMethodPrototype();
    }
}
