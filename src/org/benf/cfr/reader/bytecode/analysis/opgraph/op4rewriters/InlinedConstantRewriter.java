package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.*;

import java.util.List;
import java.util.Map;

public class InlinedConstantRewriter extends AbstractExpressionRewriter implements Op04Rewriter {

    private final Map<String, Expression> rewrites;

    public InlinedConstantRewriter(Map<String, Expression> rewrites) {
        this.rewrites = rewrites;
    }

    // TODO : This is a very common pattern - linearize is treated as a util - we should just walk.
    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        for (StructuredStatement statement : structuredStatements) {
            statement.rewriteExpressions(this);
        }
    }

    /*
     * Expression rewriter boilerplate - note that we can't expect ssaIdentifiers to be non-null.
     */
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        // would be nicer perhaps to replace this with a dictonary<Expression, Expression> rewriter.
        if (expression instanceof Literal && expression.getInferredJavaType().getJavaTypeInstance() == TypeConstants.STRING) {
            Literal exp = (Literal)expression;
            Object val = exp.getValue().getValue();
            if (val instanceof String) {
                String str = (String)val;
                Expression replacement = rewrites.get(str);
                if (replacement != null) return replacement;
            }
        }
        expression = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return expression;
    }
}
