package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredReturn;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:40
 * To change this template use File | Settings | File Templates.
 */
public class ReturnValueStatement extends ReturnStatement {
    private Expression rvalue;

    public ReturnValueStatement(Expression rvalue) {
        this.rvalue = rvalue;
    }

    @Override
    public void dump(Dumper dumper) {
        dumper.print("return " + rvalue.toString() + ";\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        this.rvalue = rvalue.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        this.rvalue = expressionRewriter.rewriteExpression(rvalue, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredReturn(rvalue);
    }
}
