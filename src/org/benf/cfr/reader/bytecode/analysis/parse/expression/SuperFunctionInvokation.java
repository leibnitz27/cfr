package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class SuperFunctionInvokation extends AbstractMemberFunctionInvokation {
    private final boolean isOnInterface;
    private final JavaTypeInstance typeName;

    public SuperFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, Expression object, List<Expression> args, List<Boolean> nulls, boolean isOnInterface) {
        super(cp, function, object, args, nulls);
        this.isOnInterface = isOnInterface;
        this.typeName = null;
    }

    private SuperFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, Expression object, List<Expression> args, List<Boolean> nulls, boolean isOnInterface, JavaTypeInstance name) {
        super(cp, function, object, args, nulls);
        this.isOnInterface = isOnInterface;
        this.typeName = name;
    }

    public SuperFunctionInvokation withCustomName(JavaTypeInstance name) {
        return new SuperFunctionInvokation(getCp(), getFunction(), getObject(), getArgs(), getNulls(), isOnInterface, name);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new SuperFunctionInvokation(getCp(), getFunction(), cloneHelper.replaceOrClone(getObject()), cloneHelper.replaceOrClone(getArgs()), getNulls(), isOnInterface, typeName);
    }

    public boolean isEmptyIgnoringSynthetics() {
        MethodPrototype prototype = getMethodPrototype();
        for (int i=0, len=prototype.getArgs().size();i<len;++i) {
            if (!prototype.isHiddenArg(i)) return false;
        }
        return true;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        if (isOnInterface) {
            collector.collect(getFunction().getClassEntry().getTypeInstance());
        }
        collector.collect(typeName);
        super.collectTypeUsages(collector);
    }

    public boolean isInit() {
        return getMethodPrototype().getName().equals(MiscConstants.INIT_METHOD);
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
            // Let there now be a rant about how default methods on super classes allowed
            // multiple inheritance to sneak in by the back door.  Seriously, what?!
            if (isOnInterface) {
                d.dump(getFunction().getClassEntry().getTypeInstance()).print(".");
            }
            if (this.typeName != null) {
                d.dump(this.typeName).print(".");
            }
            d.print("super.").print(methodPrototype.getFixedName()).print("(");
        }
        boolean first = true;

        for (int x = 0; x < args.size(); ++x) {
            if (methodPrototype.isHiddenArg(x)) continue;
            Expression arg = args.get(x);
            if (!first) d.print(", ");
            first = false;
            methodPrototype.dumpAppropriatelyCastedArgumentString(arg, d);
        }
        d.print(")");
        return d;
    }

    public String getName() {
        return "super";
    }
}
