package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class MemberFunctionInvokation extends AbstractMemberFunctionInvokation {
    private final boolean special;
    private final boolean isInitMethod;

    public MemberFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, Expression object, JavaTypeInstance bestType, boolean special, List<Expression> args, List<Boolean> nulls) {
        super(cp, function, object, bestType, args, nulls);
        // Most of the time a member function invokation for a constructor will
        // get pulled up into a constructorInvokation, however, when it's a super call, it won't.
        this.isInitMethod = function.isInitMethod();
        this.special = special;
    }

    private MemberFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, Expression object, boolean special, List<Expression> args, List<Boolean> nulls) {
        super(cp, function, object, args, nulls);
        this.isInitMethod = function.isInitMethod();
        this.special = special;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new MemberFunctionInvokation(getCp(), getFunction(), cloneHelper.replaceOrClone(getObject()), special, cloneHelper.replaceOrClone(getArgs()), getNulls());
    }

    public MemberFunctionInvokation withReplacedObject(Expression object) {
        return new MemberFunctionInvokation(getCp(), getFunction(), object, special, getArgs(), getNulls());
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        getObject().dumpWithOuterPrecedence(d, getPrecedence(), Troolean.NEITHER);

        MethodPrototype methodPrototype = getMethodPrototype();
        if (!isInitMethod) d.print(".").methodName(getFixedName(), methodPrototype, false);
        d.print("(");
        List<Expression> args = getArgs();
        boolean first = true;
        for (int x = 0; x < args.size(); ++x) {
            if (methodPrototype.isHiddenArg(x)) continue;
            Expression arg = args.get(x);
            first = StringUtils.comma(first, d);
            methodPrototype.dumpAppropriatelyCastedArgumentString(arg, d);
        }
        d.print(")");
        return d;
    }

    public boolean isInitMethod() {
        return isInitMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        if (o == this) return true;
        if (!(o instanceof MemberFunctionInvokation)) return false;
        return getName().equals(((MemberFunctionInvokation) o).getName());
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (!super.equivalentUnder(o, constraint)) return false;
        if (o == this) return true;
        if (!(o instanceof MemberFunctionInvokation)) return false;
        MemberFunctionInvokation other = (MemberFunctionInvokation) o;
        return constraint.equivalent(getName(), other.getName());
    }
}
