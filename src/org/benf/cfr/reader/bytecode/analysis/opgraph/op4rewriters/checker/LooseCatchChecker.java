package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.checker;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredCatch;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredTry;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.DecompilerComments;

public class LooseCatchChecker implements Op04Checker {
    private boolean looseCatch = false;

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        if (looseCatch) return in;
        if (in instanceof StructuredCatch) {
            // Then we require the scope above this to be a try, otherwise it's an issue.
            StructuredStatement outer = scope.get(1);
            if (!(outer instanceof StructuredTry)) {
                looseCatch = true;
                return in;
            }
        }
        in.transformStructuredChildren(this, scope);
        return in;
    }

    @Override
    public void commentInto(DecompilerComments comments) {
        if (looseCatch) {
            comments.addComment(DecompilerComment.LOOSE_CATCH_BLOCK);
        }
    }
}
