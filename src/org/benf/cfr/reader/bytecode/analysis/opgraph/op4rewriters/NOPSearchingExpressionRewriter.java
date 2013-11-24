package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 24/11/2013
 * Time: 13:04
 */
public class NOPSearchingExpressionRewriter extends AbstractExpressionRewriter {

    private final Expression needle;
    transient boolean found = false;

    public NOPSearchingExpressionRewriter(Expression needle) {
        this.needle = needle;
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (needle.equals(expression)) {
            found = true;
        }
        return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
    }

    public boolean isFound() {
        return found;
    }
}
