package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 18/02/2014
 * Time: 05:28
 */
public class ExpressionRewriterTransformer implements StructuredStatementTransformer {
    private final ExpressionRewriter expressionRewriter;

    public ExpressionRewriterTransformer(ExpressionRewriter expressionRewriter) {
        this.expressionRewriter = expressionRewriter;
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        // This is incapable of fundamentally changing the statement type.
        // Need a different rewriter if we're going to do that.
        in.rewriteExpressions(expressionRewriter);
        in.transformStructuredChildren(this, scope);
        return in;
    }
}
