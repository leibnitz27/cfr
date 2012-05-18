package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.LinkedList;

/**
 * Created:
 * User: lee
 * Date: 14/05/2012
 */
public class Block extends AbstractStructuredStatement {
    private LinkedList<Op04StructuredStatement> containedStatements;
    private boolean indenting;

    public Block(LinkedList<Op04StructuredStatement> containedStatements, boolean indenting) {
        this.containedStatements = containedStatements;
        this.indenting = indenting;
    }

    @Override
    public void dump(Dumper dumper) {
        try {
            dumper.print("{\n");
            dumper.indent(1);
            for (Op04StructuredStatement structuredBlock : containedStatements) {
                structuredBlock.dump(dumper);
            }
        } finally {
            dumper.indent(-1);
            dumper.print("}\n");
        }
    }
}
