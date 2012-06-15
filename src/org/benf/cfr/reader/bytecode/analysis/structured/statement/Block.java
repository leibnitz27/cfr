package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
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

    // At the end of a loop, we either jump back, jump out, or return.
    public boolean removeLastContinue(BlockIdentifier block) {
        StructuredStatement structuredStatement = containedStatements.getLast().getStructuredStatement();
        if (structuredStatement instanceof StructuredContinue) {
            StructuredContinue structuredContinue = (StructuredContinue) structuredStatement;
            if (structuredContinue.getContinueTgt() == block) {
                Op04StructuredStatement continueStmt = containedStatements.getLast();
                continueStmt.replaceStatementWithNOP("");
                return true;
            } else {
                return false;
            }
        } else if (structuredStatement instanceof StructuredWhile) {
            return false;
        } else if (structuredStatement instanceof StructuredReturn) {
            return false;
        } else if (structuredStatement instanceof StructuredThrow) {
            return false;
        } else {
            throw new ConfusedCFRException("Trying to remove last continue of a block, but it's not a valid loop end " + containedStatements.getLast());
        }
    }

    public boolean removeLastGoto() {
        StructuredStatement structuredStatement = containedStatements.getLast().getStructuredStatement();
        if (structuredStatement instanceof UnstructuredGoto) {
            Op04StructuredStatement oldGoto = containedStatements.getLast();
            oldGoto.replaceStatementWithNOP("");
            return true;
        } else if (structuredStatement instanceof StructuredBreak) {
            return false;
        } else if (structuredStatement instanceof StructuredContinue) {
            return false;
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
