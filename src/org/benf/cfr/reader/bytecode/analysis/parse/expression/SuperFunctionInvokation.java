package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodRef;
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
public class SuperFunctionInvokation extends AbstractFunctionInvokation {

    public SuperFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, MethodPrototype methodPrototype, Expression object, List<Expression> args) {
        super(cp, function, methodPrototype, object, args);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new SuperFunctionInvokation(getCp(), getFunction(), getMethodPrototype(), cloneHelper.replaceOrClone(getObject()), cloneHelper.replaceOrClone(getArgs()));
    }

    private boolean isSyntheticThisFirstArg() {
        JavaTypeInstance superType = getFunction().getClassEntry().getTypeInstance();
        return superType.getInnerClassHereInfo().isHideSyntheticThis();
    }

    public boolean isEmptyIgnoringSynthetics() {
        return (getArgs().size() == (isSyntheticThisFirstArg() ? 1 : 0));
    }

    @Override
    public Dumper dump(Dumper d) {
        MethodPrototype methodPrototype = getMethodPrototype();
        List<Expression> args = getArgs();
        if (methodPrototype.getName().equals(MiscConstants.INIT_METHOD)) {
            d.print("super(");
        } else {
            d.print("super.").print(methodPrototype.getName()).print("(");
        }
        boolean first = true;

        int start = isSyntheticThisFirstArg() ? 1 : 0;
        for (int x = start; x < args.size(); ++x) {
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
