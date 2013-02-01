package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredCase extends AbstractStructuredStatement {
    private List<Expression> values;
    private Op04StructuredStatement body;
    private final BlockIdentifier blockIdentifier;

    public StructuredCase(List<Expression> values, Op04StructuredStatement body, BlockIdentifier blockIdentifier) {
        this.values = values;
        this.body = body;
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public void dump(Dumper dumper) {
        if (values.isEmpty()) {
            dumper.print("default: ");
        } else {
            for (Expression value : values) {
                dumper.print("case " + value + ": ");
            }
        }
        body.dump(dumper);
    }

    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    public List<Expression> getValues() {
        return values;
    }

    public Op04StructuredStatement getBody() {
        return body;
    }

    public BlockIdentifier getBlockIdentifier() {
        return blockIdentifier;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
        body.transform(transformer);
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(this);
        body.linearizeStatementsInto(out);
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
