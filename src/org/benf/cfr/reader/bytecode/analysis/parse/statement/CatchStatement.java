package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
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
    private BlockIdentifier catchBlockIdent;
    private LValue catching;

    public CatchStatement(List<ExceptionGroup.Entry> exceptions, LValue catching) {
        this.exceptions = exceptions;
        this.catching = catching;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("catch ( " + exceptions + " " + catching + " ) {\n");
    }

    public BlockIdentifier getCatchBlockIdent() {
        return catchBlockIdent;
    }

    public void setCatchBlockIdent(BlockIdentifier catchBlockIdent) {
        this.catchBlockIdent = catchBlockIdent;
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
    }

    @Override
    public LValue getCreatedLValue() {
        return catching;
    }

    @Override
    public String toString() {
        return "CatchStatement";
    }

    public List<ExceptionGroup.Entry> getExceptions() {
        return exceptions;
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new UnstructuredCatch(exceptions, catchBlockIdent, catching);
    }
}
