package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.util.output.Dumper;

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
}
