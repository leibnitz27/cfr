package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.ExpressionRewriterTransformer;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MethodHandlePlaceholder;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.FakeMethod;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageCollectingDumper;

public class FakeMethodRewriter {
    public static void rewrite(ClassFile classFile, TypeUsageCollectingDumper typeUsage) {
        ExpressionRewriterTransformer trans = new ExpressionRewriterTransformer(new Rewriter(classFile, typeUsage));
        for (Method method : classFile.getMethods()) {
            if (method.hasCodeAttribute()) {
                Op04StructuredStatement code = method.getAnalysis();
                trans.transform(code);
            }
        }
    }

    private static class Rewriter extends AbstractExpressionRewriter {
        private final ClassFile classFile;
        private TypeUsageCollectingDumper typeUsage;

        Rewriter(ClassFile classFile, TypeUsageCollectingDumper typeUsage) {
            this.classFile = classFile;
            this.typeUsage = typeUsage;
        }

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (expression instanceof MethodHandlePlaceholder) {
                FakeMethod method = ((MethodHandlePlaceholder) expression).addFakeMethod(classFile);
                typeUsage.dump(method);
            }
            return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        }
    }
}
