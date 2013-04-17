package org.benf.cfr.reader.bytecode.analysis.structured.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 17/04/2013
 * Time: 06:53
 */
public class StructuredStatementExpression extends AbstractExpression {

    private StructuredStatement content;

    public StructuredStatementExpression(InferredJavaType inferredJavaType, StructuredStatement content) {
        super(inferredJavaType);
        this.content = content;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        Dumper stringDumper = new ToStringDumper();
        content.dump(stringDumper);
        return stringDumper.toString();
    }
}
