package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredSwitch extends AbstractStructuredBlockStatement implements BoxingProcessor {
    private Expression switchOn;
    private final BlockIdentifier blockIdentifier;

    public StructuredSwitch(Expression switchOn, Op04StructuredStatement body, BlockIdentifier blockIdentifier) {
        super(body);
        this.switchOn = switchOn;
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        switchOn.collectTypeUsages(collector);
        super.collectTypeUsages(collector);
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("switch (").dump(switchOn).print(") ");
        getBody().dump(dumper);
        return dumper;
    }

    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
        scope.add(this);
        try {
            getBody().transform(transformer, scope);
        } finally {
            scope.remove(this);
        }
    }

    public boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter) {
        switchOn = boxingRewriter.sugarUnboxing(switchOn);
        return true;
    }

    public Expression getSwitchOn() {
        return switchOn;
    }

    public Op04StructuredStatement getBody() {
        return super.getBody();
    }

    public BlockIdentifier getBlockIdentifier() {
        return blockIdentifier;
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        getBody().linearizeStatementsInto(out);
    }

    @Override
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        switchOn.collectUsedLValues(scopeDiscoverer);
        getBody().traceLocalVariableScope(scopeDiscoverer);
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredSwitch)) return false;
        StructuredSwitch other = (StructuredSwitch) o;
        if (!switchOn.equals(other.switchOn)) return false;
        if (!blockIdentifier.equals(other.blockIdentifier)) return false;
        matchIterator.advance();
        return true;
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        expressionRewriter.handleStatement(this.getContainer());
        switchOn = expressionRewriter.rewriteExpression(switchOn, null, this.getContainer(), null);
    }

}
