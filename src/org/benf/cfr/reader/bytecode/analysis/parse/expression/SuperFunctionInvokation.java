package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class SuperFunctionInvokation extends AbstractMemberFunctionInvokation {

    public SuperFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, Expression object, List<Expression> args, List<Boolean> nulls) {
        super(cp, function, object, args, nulls);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new SuperFunctionInvokation(getCp(), getFunction(), cloneHelper.replaceOrClone(getObject()), cloneHelper.replaceOrClone(getArgs()), getNulls());
    }

    public boolean isEmptyIgnoringSynthetics() {
        MethodPrototype prototype = getMethodPrototype();
        for (int i=0, len=prototype.getArgs().size();i<len;++i) {
            if (!prototype.isHiddenArg(i)) return false;
        }
        return true;
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        MethodPrototype methodPrototype = getMethodPrototype();
        List<Expression> args = getArgs();
        if (methodPrototype.getName().equals(MiscConstants.INIT_METHOD)) {
            d.print("super(");
        } else {
            d.print("super.").print(methodPrototype.getFixedName()).print("(");
        }
        boolean first = true;

        for (int x = 0; x < args.size(); ++x) {
            if (methodPrototype.isHiddenArg(x)) continue;
            Expression arg = args.get(x);
            if (!first) d.print(", ");
            first = false;
            methodPrototype.dumpAppropriatelyCastedArgumentString(arg, x, d);
        }
        d.print(")");
        return d;
    }

    public String getName() {
        return "super";
    }


}
