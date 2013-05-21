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
public class ConstructorInvokationSimple extends AbstractConstructorInvokation {

    public ConstructorInvokationSimple(InferredJavaType inferredJavaType, List<Expression> args) {
        super(inferredJavaType, args);
    }

    @Override
    public Dumper dump(Dumper d) {
        JavaTypeInstance clazz = super.getTypeInstance();
        InnerClassInfo innerClassInfo = clazz.getInnerClassHereInfo();
        List<Expression> args = getArgs();

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
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof ConstructorInvokationSimple)) return false;

        return super.equals(o);
    }
}
