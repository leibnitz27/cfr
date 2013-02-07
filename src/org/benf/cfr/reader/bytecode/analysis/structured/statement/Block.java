package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.LinkedList;
import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 14/05/2012
 */
public class Block extends AbstractStructuredStatement {
    private LinkedList<Op04StructuredStatement> containedStatements;
    private boolean indenting;

    private final static LinkedList<Op04StructuredStatement> emptyBlockStatements = ListFactory.newLinkedList();

    public Block(LinkedList<Op04StructuredStatement> containedStatements, boolean indenting) {
        this.containedStatements = containedStatements;
        this.indenting = indenting;
    }

    public static Block getEmptyBlock() {
        return new Block(emptyBlockStatements, false);
    }

    public boolean removeLastContinue(BlockIdentifier block) {
        StructuredStatement structuredStatement = containedStatements.getLast().getStructuredStatement();
        if (structuredStatement instanceof AbstractStructuredContinue) {
            AbstractStructuredContinue structuredContinue = (AbstractStructuredContinue) structuredStatement;
            if (structuredContinue.getContinueTgt() == block) {
                Op04StructuredStatement continueStmt = containedStatements.getLast();
                continueStmt.replaceStatementWithNOP("");
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean removeLastNVReturn() {
        StructuredStatement structuredStatement = containedStatements.getLast().getStructuredStatement();
        if (structuredStatement instanceof StructuredReturn) {
            Op04StructuredStatement oldReturn = containedStatements.getLast();
            StructuredReturn structuredReturn = (StructuredReturn) structuredStatement;
            if (structuredReturn.getValue() == null) {
                oldReturn.replaceStatementWithNOP("");
            }
            return true;
        } else {
            return false;
        }
    }

    // TODO : This is unsafe.  Replace with version which requires target.
    public boolean removeLastGoto() {
        StructuredStatement structuredStatement = containedStatements.getLast().getStructuredStatement();
        if (structuredStatement instanceof UnstructuredGoto) {
            Op04StructuredStatement oldGoto = containedStatements.getLast();
            oldGoto.replaceStatementWithNOP("");
            return true;
        } else {
            return false;
        }
    }

    public boolean removeLastGoto(Op04StructuredStatement toHere) {
        StructuredStatement structuredStatement = containedStatements.getLast().getStructuredStatement();
        if (structuredStatement instanceof UnstructuredGoto) {
            Op04StructuredStatement oldGoto = containedStatements.getLast();
            if (oldGoto.getTargets().get(0) == toHere) {
                oldGoto.replaceStatementWithNOP("");
                return true;
            }
        }
        return false;
    }

    public UnstructuredWhile removeLastEndWhile() {
        StructuredStatement structuredStatement = containedStatements.getLast().getStructuredStatement();
        if (structuredStatement instanceof UnstructuredWhile) {
            Op04StructuredStatement endWhile = containedStatements.getLast();
            endWhile.replaceStatementWithNOP("");
            return (UnstructuredWhile) structuredStatement;
        } else {
            throw new ConfusedCFRException("Trying to remove last while of a block, but it's not an unstructured WHILE");
        }
    }

    public boolean isJustOneStatement() {
        return containedStatements.size() == 1;
    }

    public Op04StructuredStatement getSingleStatement() {
        if (containedStatements.size() != 1) {
            throw new IllegalStateException();
        }
        return containedStatements.get(0);
    }

    public void combineTryCatch() {
        int size = containedStatements.size();
        for (int x = 0; x < size; ++x) {
            Op04StructuredStatement statement = containedStatements.get(x);
            if (statement.getStructuredStatement() instanceof StructuredTry) {
                StructuredTry structuredTry = (StructuredTry) statement.getStructuredStatement();
                ++x;
                Op04StructuredStatement next = containedStatements.get(x);
                while (x < size && next.getStructuredStatement() instanceof StructuredCatch) {
                    structuredTry.addCatch(next.nopThisAndReplace());
                    ++x;
                    if (x < size) {
                        next = containedStatements.get(x);
                    } else {
                        // We'll have to find some other way of getting the next statement, probably need a DFS :(
                        next = null;
                    }
                }
                if (next != null) {
                    structuredTry.removeFinalJumpsTo(next);
                    --x;
                }
            }
        }
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer) {
        for (Op04StructuredStatement structuredBlock : containedStatements) {
            structuredBlock.transform(transformer);
        }
    }

    public List<Op04StructuredStatement> getBlockStatements() {
        return containedStatements;
    }

    @Override
    public void linearizeInto(List<StructuredStatement> out) {
        out.add(new BeginBlock());
        for (Op04StructuredStatement structuredBlock : containedStatements) {
            structuredBlock.linearizeStatementsInto(out);
        }
        out.add(new EndBlock());
    }

    @Override
    public void dump(Dumper dumper) {
        if (containedStatements.isEmpty()) {
            dumper.print("\n");
            return;
        }
        ;
        try {
            dumper.print("{\n");
            dumper.indent(1);
            for (Op04StructuredStatement structuredBlock : containedStatements) {
                structuredBlock.dump(dumper);
            }
        } finally {
            dumper.indent(-1);
            dumper.print("}");
            dumper.enqueuePendingCarriageReturn();
        }
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        throw new UnsupportedOperationException();
    }
}
