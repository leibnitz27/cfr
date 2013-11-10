package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryNameAndType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:26
 * To change this template use File | Settings | File Templates.
 */
public class MemberFunctionInvokation extends AbstractFunctionInvokation {
    private final String name;
    private final boolean special;
    private final boolean isInitMethod;

    public MemberFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, MethodPrototype methodPrototype, Expression object, boolean special, List<Expression> args) {
        super(cp, function, methodPrototype, object, args);
        ConstantPoolEntryNameAndType nameAndType = function.getNameAndTypeEntry();
        String funcName = nameAndType.getName().getValue();
        // Most of the time a member function invokation for a constructor will
        // get pulled up into a constructorInvokation, however, when it's a super call, it won't.

        this.isInitMethod = function.isInitMethod();
        this.name = funcName;
        this.special = special;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new MemberFunctionInvokation(getCp(), getFunction(), getMethodPrototype(), cloneHelper.replaceOrClone(getObject()), special, cloneHelper.replaceOrClone(getArgs()));
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        super.collectTypeUsages(collector);
    }

    @Override
    public Dumper dump(Dumper d) {
        String comment = null;
        d.dump(getObject());

        if (!isInitMethod) d.print("." + name);
        d.print("(");
        List<Expression> args = getArgs();
        boolean first = true;
        for (int x = 0; x < args.size(); ++x) {
            Expression arg = args.get(x);
            if (!first) d.print(", ");
            first = false;
            getMethodPrototype().dumpAppropriatelyCastedArgumentString(arg, x, d);
        }
        d.print(")");
        if (comment != null) d.print(comment);
        return d;
    }

    public boolean isInitMethod() {
        return isInitMethod;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        if (o == this) return true;
        if (!(o instanceof MemberFunctionInvokation)) return false;
        return name.equals(((MemberFunctionInvokation) o).name);
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (!super.equivalentUnder(o, constraint)) return false;
        if (o == this) return true;
        if (!(o instanceof MemberFunctionInvokation)) return false;
        MemberFunctionInvokation other = (MemberFunctionInvokation) o;
        return constraint.equivalent(name, other.name);
    }
}
