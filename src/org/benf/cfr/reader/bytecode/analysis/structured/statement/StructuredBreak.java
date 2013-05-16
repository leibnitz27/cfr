package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
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
public class StructuredBreak extends AbstractStructuredStatement {

    private final BlockIdentifier breakBlock;
    private final boolean localBreak;

    public StructuredBreak(BlockIdentifier breakBlock, boolean localBreak) {
        this.breakBlock = breakBlock;
        this.localBreak = localBreak;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (localBreak) {
            dumper.print("break;\n");
        } else {
            dumper.print("break " + breakBlock.getName() + ";\n");
        }
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
        if (!(o instanceof StructuredBreak)) return false;
        StructuredBreak other = (StructuredBreak) o;
        if (!breakBlock.equals(other.breakBlock)) return false;
        // Don't check locality.
        matchIterator.advance();
        return true;
    }


    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
    }

}
