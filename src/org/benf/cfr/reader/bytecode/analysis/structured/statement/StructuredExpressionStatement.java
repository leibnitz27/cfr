package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredExpressionStatement extends AbstractStructuredStatement {
    private Expression expression;
    private boolean inline;

    public StructuredExpressionStatement(Expression expression, boolean inline) {
        this.expression = expression;
        this.inline = inline;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.dump(expression);
        if (!inline) dumper.endCodeln();
        return dumper;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredExpressionStatement)) return false;
        StructuredExpressionStatement other = (StructuredExpressionStatement) o;
        if (!expression.equals(other.expression)) return false;
        matchIterator.advance();
        return true;
    }


    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        expression = expressionRewriter.rewriteExpression(expression, null, this.getContainer(), null);
    }

}
