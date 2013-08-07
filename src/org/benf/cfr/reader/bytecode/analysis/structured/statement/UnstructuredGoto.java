package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Set;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class UnstructuredGoto extends AbstractUnStructuredStatement {

    public UnstructuredGoto() {
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("** GOTO " + getContainer().getTargetLabel(0) + "\n");
    }

    public StructuredStatement transformWithScope(StructuredScope scope) {
        Set<Op04StructuredStatement> nextFallThrough = scope.getNextFallThrough(this);
        Op04StructuredStatement target = getContainer().getTargets().get(0);
        if (nextFallThrough.contains(target)) {
            // Ok, fell through.  If we're the last statement of the current scope,
            // and the current scope has fallthrough, we can be removed.  Otherwise we
            // need to be translated to a break.
            if (scope.statementIsLast(this)) {
                return new StructuredComment("");
            } else {
                return this;
            }
        }
        return this;
    }
}
