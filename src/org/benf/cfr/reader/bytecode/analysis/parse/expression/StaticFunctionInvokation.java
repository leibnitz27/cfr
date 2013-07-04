package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.ConstantPoolEntryNameAndType;
import org.benf.cfr.reader.util.output.Dumper;

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
    private final JavaTypeInstance clazz;

    private static InferredJavaType getTypeForFunction(ConstantPoolEntryMethodRef function, List<Expression> args) {
        InferredJavaType res = new InferredJavaType(
                function.getMethodPrototype().getReturnType(function.getClassEntry().getTypeInstance(), args),
                InferredJavaType.Source.EXPRESSION);
        return res;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new StaticFunctionInvokation(function, cloneHelper.replaceOrClone(args));
    }

    public StaticFunctionInvokation(ConstantPoolEntryMethodRef function, List<Expression> args) {
        super(getTypeForFunction(function, args));
        this.function = function;
        this.args = args;
        this.clazz = function.getClassEntry().getTypeInstance();
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
    public Dumper dump(Dumper d) {
        d.print(clazz.toString() + ".");
        ConstantPoolEntryNameAndType nameAndType = function.getNameAndTypeEntry();
        d.print(nameAndType.getName().getValue() + "(");
        boolean first = true;
        for (Expression arg : args) {
            if (!first) d.print(", ");
            first = false;
            d.dump(arg);
        }
        d.print(")");
        return d;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        for (Expression expression : args) {
            expression.collectUsedLValues(lValueUsageCollector);
        }
    }


    public String getName() {
        ConstantPoolEntryNameAndType nameAndType = function.getNameAndTypeEntry();
        return nameAndType.getName().getValue();
    }

    public JavaTypeInstance getClazz() {
        return clazz;
    }

    public List<Expression> getArgs() {
        return args;
    }

    public ConstantPoolEntryMethodRef getFunction() {
        return function;
    }
}
