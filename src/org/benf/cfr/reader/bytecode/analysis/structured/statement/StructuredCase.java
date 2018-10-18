package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.annotation.Nullable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public class StructuredCase extends AbstractStructuredBlockStatement {
    private List<Expression> values;
    private final BlockIdentifier blockIdentifier;
    @Nullable
    private final InferredJavaType inferredJavaTypeOfSwitch;
    // Because enum values inside a switch are written without the class name, (but ONLY in a switch
    // on that enum!) we have to know about the context of usage.
    private final boolean enumSwitch;

    public StructuredCase(List<Expression> values, InferredJavaType inferredJavaTypeOfSwitch, Op04StructuredStatement body, BlockIdentifier blockIdentifier) {
        this(values, inferredJavaTypeOfSwitch, body, blockIdentifier, false);
    }

    public StructuredCase(List<Expression> values, InferredJavaType inferredJavaTypeOfSwitch, Op04StructuredStatement body, BlockIdentifier blockIdentifier, boolean enumSwitch) {
        super(body);
        this.blockIdentifier = blockIdentifier;
        this.enumSwitch = enumSwitch;
        this.inferredJavaTypeOfSwitch = inferredJavaTypeOfSwitch;
        if (inferredJavaTypeOfSwitch != null && inferredJavaTypeOfSwitch.getJavaTypeInstance() == RawJavaType.CHAR) {
            for (Expression value : values) {
                if (value instanceof Literal) {
                    TypedLiteral typedLiteral = ((Literal) value).getValue();
                    typedLiteral.getInferredJavaType().useAsWithoutCasting(inferredJavaTypeOfSwitch.getJavaTypeInstance());
                }
            }
        }
        this.values = values;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        if (inferredJavaTypeOfSwitch != null) collector.collect(inferredJavaTypeOfSwitch.getJavaTypeInstance());
        collector.collectFrom(values);
        super.collectTypeUsages(collector);
    }

    private static StaticVariable getEnumStatic(Expression expression) {
        if (!(expression instanceof LValueExpression)) return null;
        LValue lValue = ((LValueExpression) expression).getLValue();
        if (!(lValue instanceof StaticVariable)) return null;
        return (StaticVariable) lValue;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (values.isEmpty()) {
            dumper.print("default: ");
        } else {
            for (int x = 0, len = values.size(), last = len - 1; x < len; ++x) {
                Expression value = values.get(x);
                if (enumSwitch) {
                    // value should be an lvalue expression containing a static enum value.
                    // don't show the case part of that.
                    StaticVariable enumStatic = getEnumStatic(value);
                    if (enumStatic != null) {
                        dumper.print("case " + enumStatic.getFieldName() + ": ");
                        if (x != last) dumper.newln();
                        continue;
                    }
                }
                dumper.print("case ").dump(value).print(": ");
                if (x != last) dumper.newln();
            }
        }
        getBody().dump(dumper);
        return dumper;
    }

    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    public List<Expression> getValues() {
        return values;
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
        for (Expression expression : values) {
            expression.collectUsedLValues(scopeDiscoverer);
        }
        scopeDiscoverer.processOp04Statement(getBody());
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredCase)) return false;
        StructuredCase other = (StructuredCase) o;
        if (!values.equals(other.values)) return false;
        if (!blockIdentifier.equals(other.blockIdentifier)) return false;
        matchIterator.advance();
        return true;
    }


    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
        // Values in a case statement must be literals, not amenable.
    }

}
