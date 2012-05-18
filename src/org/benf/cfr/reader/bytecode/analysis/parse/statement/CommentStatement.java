package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * To change this template use File | Settings | File Templates.
 */
public class CommentStatement extends AbstractStatement {
    private final String text;

    public CommentStatement(String text) {
        this.text = text;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print(text + "\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueCollector lValueCollector, SSAIdentifiers ssaIdentifiers) {
    }

    @Override
    public String toString() {
        return "CommentStatement : " + text;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredComment(text);
    }
}
