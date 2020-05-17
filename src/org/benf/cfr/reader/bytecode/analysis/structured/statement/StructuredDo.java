package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.output.Dumper;

public class StructuredDo extends AbstractStructuredConditionalLoopStatement {

    private StructuredDo(ConditionalExpression condition, Op04StructuredStatement body, BlockIdentifier block) {
        super(condition, block, body);
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (block.hasForeignReferences()) dumper.label(block.getName(), true);
        dumper.print("do ");
        getBody().dump(dumper);
        dumper.removePendingCarriageReturn();
        dumper.print(" while (");
        if (condition == null) {
            dumper.print("true");
        } else {
            dumper.dump(condition);
        }
        return dumper.print(");").newln();
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        StructuredStatement o = matchIterator.getCurrent();
        if (!(o instanceof StructuredDo)) return false;
        StructuredDo other = (StructuredDo) o;
        if (condition == null) {
            if (other.condition != null) return false;
        } else {
            if (!condition.equals(other.condition)) return false;
        }
        if (!block.equals(other.block)) return false;
        // Don't check locality.
        matchIterator.advance();
        return true;
    }

    // https://github.com/leibnitz27/cfr/issues/167
    // At great personal cost, I concur that the majority of developers are crazy, and prefer
    // while(true) {}
    // to
    // do {} while (true).
    public static AbstractStructuredConditionalLoopStatement create(ConditionalExpression condition, Op04StructuredStatement body, BlockIdentifier block) {
        if (condition == null) {
            return new StructuredWhile(null, body, block);
        }
        return new StructuredDo(condition, body, block);
    }
}
