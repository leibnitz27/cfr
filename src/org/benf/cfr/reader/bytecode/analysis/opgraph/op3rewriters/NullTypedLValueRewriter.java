package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;

public class NullTypedLValueRewriter extends AbstractExpressionRewriter {
    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        InferredJavaType inferredJavaType = lValue.getInferredJavaType();
        JavaTypeInstance javaTypeInstance = inferredJavaType.getJavaTypeInstance();
        if (javaTypeInstance == RawJavaType.NULL || javaTypeInstance == RawJavaType.VOID) {
            inferredJavaType.applyKnownBaseType();
        }
        return lValue;
    }
}
