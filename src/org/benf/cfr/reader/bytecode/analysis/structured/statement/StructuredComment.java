package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredComment extends AbstractStructuredStatement {
    private String comment;

    public StructuredComment(String comment) {
        this.comment = comment;
    }

    @Override
    public void dump(Dumper dumper) {
        if (comment.length() > 0) {
            dumper.print("// ");
            dumper.print(comment + "\n");
        }
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
    }

    // Lose the comments.
    @Override
    public void linearizeInto(List<StructuredStatement> out) {
    }

}
