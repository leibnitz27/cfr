package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredCase extends AbstractStructuredStatement {
    private List<Expression> values;
    private Op04StructuredStatement body;
    private final BlockIdentifier blockIdentifier;

    public StructuredCase(List<Expression> values, Op04StructuredStatement body, BlockIdentifier blockIdentifier) {
        this.values = values;
        this.body = body;
        this.blockIdentifier = blockIdentifier;
    }

    @Override
    public void dump(Dumper dumper) {
        if (values.isEmpty()) {
            dumper.print("default: ");
        } else {
            for (Expression value : values) {
                dumper.print("case " + value + ": ");
            }
        }
        body.dump(dumper);
    }

    @Override
    public boolean isProperlyStructured() {
        return true;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
        body.transform(transformer);
    }

}
