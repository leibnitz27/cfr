package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:44
 * To change this template use File | Settings | File Templates.
 */
public class NewPrimitiveArray extends AbstractNewArray {
    private Expression size;
    private final ArrayType type;

    public NewPrimitiveArray(Expression size, byte type) {
        this(size, ArrayType.getArrayType(type));
    }

    public NewPrimitiveArray(Expression size, ArrayType type) {
        // We don't really know anything about the array dimensionality, just the underlying type. :P
        super(new InferredJavaType(RawJavaType.REF, InferredJavaType.Source.EXPRESSION));
        this.size = size;
        this.type = type;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print("new " + type + "[").dump(size).print("]");
    }

    @Override
    public int getNumDims() {
        return 1;
    }

    @Override
    public Expression getDimSize(int dim) {
        if (dim > 0) throw new ConfusedCFRException("Only 1 dimension for primitive arrays!");
        return size;
    }

    @Override
    public JavaTypeInstance getInnerType() {
        return type.getJavaTypeInstance();
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        size = size.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        return this;
    }


    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        size = expressionRewriter.rewriteExpression(size, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof NewPrimitiveArray)) return false;
        NewPrimitiveArray other = (NewPrimitiveArray) o;
        if (!size.equals(other.size)) return false;
        if (!type.equals(other.type)) return false;
        return true;
    }
}
