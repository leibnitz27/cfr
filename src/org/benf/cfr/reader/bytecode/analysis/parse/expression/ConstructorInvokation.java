package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntryClass;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:26
 */
public class ConstructorInvokation extends AbstractExpression {
    private final ConstantPoolEntryMethodRef function;
    private final ConstantPoolEntryClass type;
    private final JavaTypeInstance clazz;
    private final List<Expression> args;
    private final ConstantPool cp;

    public ConstructorInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, ConstantPoolEntryClass type, List<Expression> args) {
        super(new InferredJavaType(type.getTypeInstance(), InferredJavaType.Source.EXPRESSION));
        this.function = function;
        this.type = type;
        this.args = args;
        this.cp = cp;
        this.clazz = type.getTypeInstance();
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

    public JavaTypeInstance getTypeInstance() {
        return type.getTypeInstance();
    }

    private Dumper toStringAsAnonymousConstruction(Dumper d) {
        // We need the inner classes on the anonymous class (!)
        ClassFile anonymousClassFile = cp.getCFRState().getClassFile(clazz, true);

        d.print("new ");
        anonymousClassFile.dumpAsAnonymousInnerClass(d);
        return d;
    }

    @Override
    public Dumper dump(Dumper d) {
        InnerClassInfo innerClassInfo = clazz.getInnerClassHereInfo();
        if (innerClassInfo.isAnoynmousInnerClass()) {
            return toStringAsAnonymousConstruction(d);
        }

        d.print("new ").print(clazz.toString()).print("(");
        boolean first = true;
        int start = innerClassInfo.isHideSyntheticThis() ? 1 : 0;
        for (int i = start; i < args.size(); ++i) {
            Expression arg = args.get(i);
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

}
