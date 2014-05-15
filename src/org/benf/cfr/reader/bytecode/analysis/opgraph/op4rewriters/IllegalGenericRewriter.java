package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractAssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericBaseInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;

public class IllegalGenericRewriter implements ExpressionRewriter {
    private final ConstantPool cp;

    public IllegalGenericRewriter(ConstantPool cp) {
        this.cp = cp;
    }

    private boolean hasIllegalGenerics(JavaTypeInstance javaTypeInstance) {
        if (!(javaTypeInstance instanceof JavaGenericBaseInstance)) return false;
        JavaGenericBaseInstance genericBaseInstance = (JavaGenericBaseInstance) javaTypeInstance;
        return genericBaseInstance.hasForeignUnbound(cp);
    }

    private void maybeRewriteExpressionType(InferredJavaType inferredJavaType) {
        JavaTypeInstance javaTypeInstance = inferredJavaType.getJavaTypeInstance();
        if (hasIllegalGenerics(javaTypeInstance)) {
            JavaTypeInstance deGenerified = javaTypeInstance.getDeGenerifiedType();
            inferredJavaType.deGenerify(deGenerified);
        }
    }

    @Override
    public void handleStatement(StatementContainer statementContainer) {

    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        maybeRewriteExpressionType(expression.getInferredJavaType());
        return expression;
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        ConditionalExpression res = (ConditionalExpression) expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return res;
    }

    @Override
    public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return expression;
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        maybeRewriteExpressionType(lValue.getInferredJavaType());
        return lValue;
    }

    @Override
    public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }
}
