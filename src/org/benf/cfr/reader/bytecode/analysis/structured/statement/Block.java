package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
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
        StructuredStatement structuredStatement = containedStatements.getLast().getStatement();
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
        StructuredStatement structuredStatement = containedStatements.getLast().getStatement();
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
        StructuredStatement structuredStatement = containedStatements.getLast().getStatement();
        if (structuredStatement instanceof UnstructuredGoto) {
            Op04StructuredStatement oldGoto = containedStatements.getLast();
            oldGoto.replaceStatementWithNOP("");
            return true;
        } else {
            return false;
        }
    }

    public boolean removeLastGoto(Op04StructuredStatement toHere) {
        StructuredStatement structuredStatement = containedStatements.getLast().getStatement();
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
        StructuredStatement structuredStatement = containedStatements.getLast().getStatement();
        if (structuredStatement instanceof UnstructuredWhile) {
            Op04StructuredStatement endWhile = containedStatements.getLast();
            endWhile.replaceStatementWithNOP("");
            return (UnstructuredWhile) structuredStatement;
        } else {
            // Not a valid xfrm
            return null;
        }
    }

    public boolean isJustOneStatement() {
        int count = 0;
        for (Op04StructuredStatement statement : containedStatements) {
            // TODO:  This is awful.
            if (!(statement.getStatement() instanceof StructuredComment)) {
                count++;
            }
        }
        return count == 1;
    }

    public Op04StructuredStatement getSingleStatement() {
        for (Op04StructuredStatement statement : containedStatements) {
            // TODO:  This is awful.
            if (!(statement.getStatement() instanceof StructuredComment)) {
                return statement;
            }
        }
        throw new IllegalStateException();
    }

    public void combineTryCatch(Op04StructuredStatement after) {
        int size = containedStatements.size();
        boolean finished = false;
        for (int x = 0; x < size && !finished; ++x) {
            Op04StructuredStatement statement = containedStatements.get(x);
            if (statement.getStatement() instanceof StructuredTry) {
                StructuredTry structuredTry = (StructuredTry) statement.getStatement();
                ++x;
                Op04StructuredStatement next = x < size - 1 ? containedStatements.get(x) : null;
                while (x < size && next != null &&
                        (next.getStatement() instanceof StructuredCatch ||
                                next.getStatement() instanceof StructuredComment ||
                                next.getStatement() instanceof StructuredFinally)) {
                    ++x;
                    if (next.getStatement() instanceof StructuredComment) {
                        next.nopThisAndReplace(); // pointless.
                        // Nothing.
                    } else if (next.getStatement() instanceof StructuredCatch) {
                        structuredTry.addCatch(next.nopThisAndReplace());
                        if (x < size) {
                            next = containedStatements.get(x);
                        } else {
                            // We'll have to find some other way of getting the next statement, probably need a DFS :(
                            next = after;
                            finished = true;
                        }
                    } else if (next.getStatement() instanceof StructuredFinally) {
                        structuredTry.addFinally(next.nopThisAndReplace());
                        if (x < size) {
                            next = containedStatements.get(x);
                        } else {
                            // We'll have to find some other way of getting the next statement, probably need a DFS :(
                            next = after;
                            finished = true;
                        }
                    }
                }
                if (next == null) next = after;
                if (next != null) {
                    structuredTry.removeFinalJumpsTo(next);
                    --x;
                }
            }
        }
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, Op04StructuredStatement after) {
        for (int x = 0, len = containedStatements.size(); x < len; ++x) {
            Op04StructuredStatement structuredBlock = containedStatements.get(x);
            Op04StructuredStatement next = x < len - 1 ? containedStatements.get(x + 1) : after;
            structuredBlock.transform(transformer, next);
        }
    }

    @Override
    public boolean isRecursivelyStructured() {
        for (Op04StructuredStatement structuredStatement : containedStatements) {
            if (!structuredStatement.isFullyStructured()) return false;
        }
        return true;
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
    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        scopeDiscoverer.enterBlock(this);

        for (Op04StructuredStatement item : containedStatements) {
            item.traceLocalVariableScope(scopeDiscoverer);
        }
        scopeDiscoverer.leaveBlock(this);
    }

    /*
     * This variable has been defined in an ENCLOSED scope, but used at this level.
     */
    @Override
    public void markCreator(LocalVariable localVariable) {
        containedStatements.addFirst(new Op04StructuredStatement(new StructuredDefinition(localVariable)));
    }

    @Override
    public Dumper dump(Dumper d) {
        if (containedStatements.isEmpty()) {
            d.print("\n");
            return d;
        }
        try {
            if (indenting) {
                d.print("{\n");
                d.indent(1);
            }
            for (Op04StructuredStatement structuredBlock : containedStatements) {
                structuredBlock.dump(d);
            }
        } finally {
            if (indenting) {
                d.indent(-1);
                d.print("}");
                d.enqueuePendingCarriageReturn();
            }
        }
        return d;
    }

    public boolean isIndenting() {
        return indenting;
    }

    @Override
    public boolean match(MatchIterator<StructuredStatement> matchIterator, MatchResultCollector matchResultCollector) {
        throw new UnsupportedOperationException();
    }


    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter) {
    }

}
