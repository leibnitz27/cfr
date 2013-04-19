package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:53
 * To change this template use File | Settings | File Templates.
 */
public class StackValue extends AbstractExpression {
    private StackSSALabel stackValue;

    public StackValue(StackSSALabel stackValue) {
        super(stackValue.getInferredJavaType());
        this.stackValue = stackValue;
    }

    @Override
    public Dumper dump(Dumper d) {
        return stackValue.dump(d);
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        Expression replaceMeWith = lValueRewriter.getLValueReplacement(stackValue, ssaIdentifiers, statementContainer);
        if (replaceMeWith != null) return replaceMeWith;
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        stackValue = expressionRewriter.rewriteExpression(stackValue, ssaIdentifiers, statementContainer, flags);
        return this;
    }

    public StackSSALabel getStackValue() {
        return stackValue;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        lValueUsageCollector.collect(stackValue);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof StackValue)) return false;
        StackValue other = (StackValue) o;
        return stackValue.equals(other.stackValue);
    }
}
