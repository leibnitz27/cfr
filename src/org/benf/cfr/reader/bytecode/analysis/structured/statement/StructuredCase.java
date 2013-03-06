package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredCase extends AbstractStructuredBlockStatement {
    private List<Expression> values;
    private final BlockIdentifier blockIdentifier;
    // Because enum values inside a switch are written without the class name, (but ONLY in a switch
    // on that enum!) we have to know about the context of usage.
    private final boolean enumSwitch;

    public StructuredCase(List<Expression> values, Op04StructuredStatement body, BlockIdentifier blockIdentifier) {
        this(values, body, blockIdentifier, false);
    }

    public StructuredCase(List<Expression> values, Op04StructuredStatement body, BlockIdentifier blockIdentifier, boolean enumSwitch) {
        super(body);
        this.values = values;
        this.blockIdentifier = blockIdentifier;
        this.enumSwitch = enumSwitch;
    }

    private static StaticVariable getEnumStatic(Expression expression) {
        if (!(expression instanceof LValueExpression)) return null;
        LValue lValue = ((LValueExpression) expression).getLValue();
        if (!(lValue instanceof StaticVariable)) return null;
        return (StaticVariable) lValue;
    }

    @Override
    public void dump(Dumper dumper) {
        if (values.isEmpty()) {
            dumper.print("default: ");
        } else {
            for (Expression value : values) {
                if (enumSwitch) {
                    // value should be an lvalue expression containing a static enum value.
                    // don't show the case part of that.
                    StaticVariable enumStatic = getEnumStatic(value);
                    if (enumStatic != null) {
                        dumper.print("case " + enumStatic.getVarName() + ": ");
                        continue;
                    }
                }
                dumper.print("case " + value + ": ");
            }
        }
        getBody().dump(dumper);
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
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
        getBody().transform(transformer);
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        getBody().linearizeStatementsInto(out);
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
}
