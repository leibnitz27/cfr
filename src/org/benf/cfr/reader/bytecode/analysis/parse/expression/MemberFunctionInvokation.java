package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.ConstantPoolEntryNameAndType;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:26
 * To change this template use File | Settings | File Templates.
 */
public class MemberFunctionInvokation extends AbstractFunctionInvokation {
    private final ConstantPoolEntryMethodRef function;
    private Expression object;
    private final List<Expression> args;
    private final ConstantPool cp;
    private final MethodPrototype methodPrototype;
    private final String name;
    private final boolean special;

    public MemberFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, MethodPrototype methodPrototype, Expression object, boolean special, List<Expression> args) {
        super(cp, function, methodPrototype, object, args);
        this.function = function;
        this.methodPrototype = methodPrototype;
        this.object = object;
        this.args = args;
        this.cp = cp;
        ConstantPoolEntryNameAndType nameAndType = function.getNameAndTypeEntry();
        String funcName = nameAndType.getName().getValue();
        // Most of the time a member function invokation for a constructor will
        // get pulled up into a constructorInvokation, however, when it's a super call, it won't.
        this.name = funcName.equals("<init>") ? null : funcName;
        this.special = special;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        object = object.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, args.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        object = expressionRewriter.rewriteExpression(object, ssaIdentifiers, statementContainer, flags);
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, expressionRewriter.rewriteExpression(args.get(x), ssaIdentifiers, statementContainer, flags));
        }
        return this;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String comment = null;
        sb.append(object.toString());

        if (name != null) sb.append(".").append(name);
        sb.append("(");
        boolean first = true;
        for (int x = 0; x < args.size(); ++x) {
            Expression arg = args.get(x);
            if (!first) sb.append(", ");
            first = false;
            sb.append(methodPrototype.getAppropriatelyCastedArgumentString(arg, x));
        }
        sb.append(")");
        if (comment != null) sb.append(comment);
        return sb.toString();
    }

    public Expression getObject() {
        return object;
    }

    public ConstantPoolEntryMethodRef getFunction() {
        return function;
    }

    public String getName() {
        return name;
    }

    public List<Expression> getArgs() {
        return args;
    }

    public Expression getAppropriatelyCastArgument(int idx) {
        return methodPrototype.getAppropriatelyCastedArgument(args.get(idx), idx);
    }

    public ConstantPool getCp() {
        return cp;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        for (Expression expression : args) {
            expression.collectUsedLValues(lValueUsageCollector);
        }
    }

}
