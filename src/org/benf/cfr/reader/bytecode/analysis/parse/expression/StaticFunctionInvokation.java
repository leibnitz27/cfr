package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
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
public class StaticFunctionInvokation extends AbstractExpression {
    private final ConstantPoolEntryMethodRef function;
    private final List<Expression> args;
    private final ConstantPool cp;
    private final JavaTypeInstance clazz;

    private static InferredJavaType getTypeForFunction(ConstantPool cp, ConstantPoolEntryMethodRef function, List<Expression> args) {
        InferredJavaType res = new InferredJavaType(
                function.getMethodPrototype().getReturnType(cp.getClassEntry(function.getClassIndex()).getTypeInstance(cp), args),
                InferredJavaType.Source.EXPRESSION);
        return res;
    }

    public StaticFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, List<Expression> args) {
        super(getTypeForFunction(cp, function, args));
        this.function = function;
        this.args = args;
        this.cp = cp;
        this.clazz = cp.getClassEntry(function.getClassIndex()).getTypeInstance(cp);
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, args.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, expressionRewriter.rewriteExpression(args.get(x), ssaIdentifiers, statementContainer, flags));
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.toString());
        sb.append(".");
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

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        for (Expression expression : args) {
            expression.collectUsedLValues(lValueUsageCollector);
        }
    }


    public String getName() {
        ConstantPoolEntryNameAndType nameAndType = cp.getNameAndTypeEntry(function.getNameAndTypeIndex());
        return nameAndType.getName(cp).getValue();
    }

    public JavaTypeInstance getClazz() {
        return clazz;
    }

    public List<Expression> getArgs() {
        return args;
    }
}
