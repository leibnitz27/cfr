package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.ArrayType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ConfusedCFRException;

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
        // We don't really know anything about the array dimensionality, just the underlying type. :P
        super(new InferredJavaType(RawJavaType.REF, InferredJavaType.Source.EXPRESSION));
        this.size = size;
        this.type = ArrayType.getArrayType(type);
    }

    @Override
    public String toString() {
        return "new " + type + "[" + size + "]";
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
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
    }

}
