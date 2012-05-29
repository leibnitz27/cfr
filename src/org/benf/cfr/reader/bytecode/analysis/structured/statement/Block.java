package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.util.ConfusedCFRException;
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

    public boolean removeLastContinue(BlockIdentifier block) {
        if (containedStatements.getLast().getStructuredStatement() instanceof StructuredContinue) {
            Op04StructuredStatement continueStmt = containedStatements.getLast();
            StructuredContinue structuredContinue = (StructuredContinue) continueStmt.getStructuredStatement();
            if (structuredContinue.getContinueTgt() == block) {
                continueStmt.replaceStatementWithNOP("");
                return true;
            } else {
                System.out.println("Can't replace " + structuredContinue.getContinueTgt() + " with " + block);
                return false;
            }
        } else {
            throw new ConfusedCFRException("Trying to remove last goto of a block, but it's not an unstructured GOTO");
        }
    }

    public boolean removeLastGoto() {
        if (containedStatements.getLast().getStructuredStatement() instanceof UnstructuredGoto) {
            Op04StructuredStatement oldGoto = containedStatements.getLast();
            oldGoto.replaceStatementWithNOP("");
            return true;
        } else {
            throw new ConfusedCFRException("Trying to remove last goto of a block, but it's not an unstructured GOTO");
        }
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
