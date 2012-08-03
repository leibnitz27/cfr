package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntry;
import org.benf.cfr.reader.entities.ConstantPoolEntryClass;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:44
 * To change this template use File | Settings | File Templates.
 */
public class NewObjectArray extends AbstractExpression {
    private Expression size;
    private final ConstantPool cp;
    private final JavaTypeInstance type;

    public NewObjectArray(Expression size, ConstantPool constantPool, ConstantPoolEntry type) {
        super(new InferredJavaType(RawJavaType.REF, InferredJavaType.Source.EXPRESSION));
        // We don't really know anything about the array dimensionality, just the underlying type. :P
        this.size = size;
        this.cp = constantPool;
        this.type = ((ConstantPoolEntryClass) type).getTypeInstance(cp);

    }

    @Override
    public String toString() {
        return "new " + type.getBeforeNewString() + "[" + size + "]" + type.getAfterNewString();
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
