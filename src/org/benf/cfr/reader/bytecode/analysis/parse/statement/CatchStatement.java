package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredCatch;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 18:08
 * To change this template use File | Settings | File Templates.
 */
public class CatchStatement extends AbstractStatement {
    private final List<ExceptionGroup.Entry> exceptions;

    public CatchStatement(List<ExceptionGroup.Entry> exceptions) {
        this.exceptions = exceptions;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("catch {\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
    }

    @Override
    public String toString() {
        return "CatchStatement";
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredCatch(exceptions);
    }
}
