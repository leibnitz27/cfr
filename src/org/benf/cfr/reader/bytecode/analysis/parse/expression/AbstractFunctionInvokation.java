package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodRef;

import java.util.List;

public abstract class AbstractFunctionInvokation extends AbstractExpression {
    private final ConstantPoolEntryMethodRef function;
    private final MethodPrototype methodPrototype;

    protected AbstractFunctionInvokation(ConstantPoolEntryMethodRef function, InferredJavaType inferredJavaType) {
        super(inferredJavaType);
        this.function = function;
        this.methodPrototype = function.getMethodPrototype();
    }

    public abstract void applyExpressionRewriterToArgs(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags);

    public abstract void setExplicitGenerics(List<JavaTypeInstance> types);

    public abstract List<JavaTypeInstance> getExplicitGenerics();

    public ConstantPoolEntryMethodRef getFunction() {
        return function;
    }

    public MethodPrototype getMethodPrototype() { return methodPrototype; }

    public String getName() {
        return methodPrototype.getName();
    }

}
