package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.entities.*;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:26
 * To change this template use File | Settings | File Templates.
 */
public class StaticFunctionInvokation implements Expression {
    private final ConstantPoolEntryMethodRef function;
    private final List<Expression> args;
    private final ConstantPool cp;

    public StaticFunctionInvokation(ConstantPool cp, ConstantPoolEntry function, List<Expression> args) {
        this.function = (ConstantPoolEntryMethodRef) function;
        this.args = args;
        this.cp = cp;
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueCollector lValueCollector, SSAIdentifiers ssaIdentifiers) {
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, args.get(x).replaceSingleUsageLValues(lValueCollector, ssaIdentifiers));
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        ConstantPoolEntryClass classEntry = cp.getClassEntry(function.getClassIndex());
        sb.append(cp.getUTF8Entry(classEntry.getNameIndex()).getValue() + ".");
        ConstantPoolEntryNameAndType nameAndType = cp.getNameAndTypeEntry(function.getNameAndTypeIndex());
        sb.append(nameAndType.getName(cp).getValue());
        sb.append("(");
        boolean first = true;
        for (Expression arg : args) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(arg.toString());
        }
        sb.append(")");
        return sb.toString();
    }
}