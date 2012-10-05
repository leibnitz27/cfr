package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
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
public class NewObject extends AbstractExpression {
    private final ConstantPool cp;
    private final ConstantPoolEntryClass type;
    private final JavaTypeInstance typeInstance;

    public NewObject(ConstantPool constantPool, ConstantPoolEntry type) {
        // TODO : we have more information than this...
        super(new InferredJavaType(((ConstantPoolEntryClass) type).getTypeInstance(constantPool), InferredJavaType.Source.EXPRESSION));
        this.cp = constantPool;
        this.type = (ConstantPoolEntryClass) type;
        this.typeInstance = ((ConstantPoolEntryClass) type).getTypeInstance(constantPool);
    }

    @Override
    public String toString() {
        return "new " + typeInstance;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }

    public ConstantPoolEntryClass getType() {
        return type;
    }

    public JavaTypeInstance getTypeInstance() {
        return typeInstance;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
    }
}
