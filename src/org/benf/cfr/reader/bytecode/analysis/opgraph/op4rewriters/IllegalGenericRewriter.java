package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractConstructorInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.FormalTypeParameter;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericBaseInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;

import java.util.List;
import java.util.Map;

public class IllegalGenericRewriter extends AbstractExpressionRewriter {
    private final ConstantPool cp;
    private final Map<String, FormalTypeParameter> formalParams;

    public IllegalGenericRewriter(ConstantPool cp, Map<String, FormalTypeParameter> formalParams) {
        this.cp = cp;
        this.formalParams = formalParams;
    }

    private boolean hasIllegalGenerics(JavaTypeInstance javaTypeInstance, boolean constructor) {
        if (!(javaTypeInstance instanceof JavaGenericBaseInstance)) return false;
        JavaGenericBaseInstance genericBaseInstance = (JavaGenericBaseInstance) javaTypeInstance;
        return genericBaseInstance.hasForeignUnbound(cp, 0, constructor, formalParams);
    }

    private void maybeRewriteExpressionType(InferredJavaType inferredJavaType, boolean constructor) {
        JavaTypeInstance javaTypeInstance = inferredJavaType.getJavaTypeInstance();
        if (hasIllegalGenerics(javaTypeInstance, constructor)) {
            JavaTypeInstance deGenerified = javaTypeInstance.getDeGenerifiedType();
            inferredJavaType.deGenerify(deGenerified);
        }
    }

    private void maybeRewriteExplicitCallTyping(AbstractFunctionInvokation abstractFunctionInvokation) {
        List<JavaTypeInstance> list = abstractFunctionInvokation.getExplicitGenerics();
        if (list == null) return;
        for (JavaTypeInstance type : list) {
            if (hasIllegalGenerics(type, false)) {
                abstractFunctionInvokation.setExplicitGenerics(null);
                return;
            }
        }
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof AbstractFunctionInvokation) {
            maybeRewriteExplicitCallTyping((AbstractFunctionInvokation)expression);
        }
        maybeRewriteExpressionType(expression.getInferredJavaType(), expression instanceof AbstractConstructorInvokation);
        return expression;
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        maybeRewriteExpressionType(lValue.getInferredJavaType(), false);
        return lValue;
    }
}
