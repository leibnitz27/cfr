package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Created:
 * User: lee
 * Date: 14/05/2012
 * <p/>
 * TODO : Block implements way more functionality than it should - move into callers.
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

    public static Block getEmptyBlock(boolean indenting) {
        return new Block(emptyBlockStatements, indenting);
    }

    public static Block getBlockFor(boolean indenting, StructuredStatement... statements) {
        LinkedList<Op04StructuredStatement> tmp = ListFactory.newLinkedList();
        for (StructuredStatement statement : statements) {
            tmp.add(new Op04StructuredStatement(statement));
        }
        return new Block(tmp, indenting);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        for (Op04StructuredStatement statement : containedStatements) {
            statement.collectTypeUsages(collector);
        }
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

    @Override
    public boolean inlineable() {
        for (Op04StructuredStatement in : containedStatements) {
            StructuredStatement s = in.getStatement();
            Class<?> c = s.getClass();
            if (!(c == StructuredReturn.class || c == UnstructuredGoto.class)) return false;
        }
        return true;
    }

    @Override
    public Op04StructuredStatement getInline() {
        return getContainer();
    }

    public void combineInlineable() {
        boolean inline = false;
        for (Op04StructuredStatement in : containedStatements) {
            if (in.getStatement().inlineable()) {
                inline = true;
                break;
            }
        }
        if (!inline) return;
        LinkedList<Op04StructuredStatement> newContained = ListFactory.newLinkedList();
        for (Op04StructuredStatement in : containedStatements) {
            StructuredStatement s = in.getStatement();
            if (s.inlineable()) {
                s.getContainer().getSources();
                Op04StructuredStatement inlinedOp = s.getInline();
                StructuredStatement inlined = inlinedOp.getStatement();
                if (inlined instanceof Block) {
                    List<Op04StructuredStatement> inlinedBlocks = ((Block) inlined).getBlockStatements();
                    newContained.addAll(((Block) inlined).getBlockStatements());
                    replaceInlineSource(in, inlinedBlocks.get(0));
                } else {
                    newContained.add(inlinedOp);
                    replaceInlineSource(in, inlinedOp);
                }
            } else {
                newContained.add(in);
            }
        }
        containedStatements = newContained;
    }

    private void replaceInlineSource(Op04StructuredStatement oldS, Op04StructuredStatement newS) {
        for (Op04StructuredStatement src : oldS.getSources()) {
            src.replaceTarget(oldS, newS);
            newS.addSource(src);
        }
        newS.getSources().remove(oldS);
    }

    public void combineTryCatch() {
        int size = containedStatements.size();
        boolean finished = false;
        for (int x = 0; x < size && !finished; ++x) {
            Op04StructuredStatement statement = containedStatements.get(x);
            if (statement.getStatement() instanceof StructuredTry) {
                StructuredTry structuredTry = (StructuredTry) statement.getStatement();
                ++x;
                Op04StructuredStatement next = x < size ? containedStatements.get(x) : null;
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
                            next = null;
                            finished = true;
                        }
                    } else if (next.getStatement() instanceof StructuredFinally) {
                        structuredTry.addFinally(next.nopThisAndReplace());
                        if (x < size) {
                            next = containedStatements.get(x);
                        } else {
                            // We'll have to find some other way of getting the next statement, probably need a DFS :(
                            next = null;
                            finished = true;
                        }
                    }
                }
                --x;
//                if (next == null) next = after;
//                if (next != null) {
//                    structuredTry.removeFinalJumpsTo(next);
//                    --x;
//                }
            }
        }
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {

        scope.add(this);
        try {
            for (int x = 0, len = containedStatements.size(); x < len; ++x) {
                Op04StructuredStatement structuredBlock = containedStatements.get(x);
                scope.setNextAtThisLevel(this, x < len - 1 ? x + 1 : -1);
                structuredBlock.transform(transformer, scope);
            }
        } finally {
            scope.remove(this);
        }
    }

    public Set<Op04StructuredStatement> getNextAfter(int x) {
        Set<Op04StructuredStatement> res = SetFactory.newSet();
        if (x == -1 || x > containedStatements.size()) return res;
        while (x != -1 && x < containedStatements.size()) {
            Op04StructuredStatement next = containedStatements.get(x);
            res.add(containedStatements.get(x));
            if (next.getStatement() instanceof StructuredComment) {
                ++x;
            } else {
                break;
            }
        }
        return res;
    }

    // Is it the last one, ignoring comments?
    public boolean statementIsLast(Op04StructuredStatement needle) {
        for (int x = containedStatements.size() - 1; x >= 0; --x) {
            Op04StructuredStatement statement = containedStatements.get(x);
            if (statement == needle) return true;
            if (statement.getStatement() instanceof StructuredComment) continue;
            break;
        }
        return false;
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
        out.add(new BeginBlock(this));
        for (Op04StructuredStatement structuredBlock : containedStatements) {
            structuredBlock.linearizeStatementsInto(out);
        }
        out.add(new EndBlock(this));
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
    public boolean alwaysDefines(LocalVariable localVariable) {
        return false;
    }

    @Override
    public Dumper dump(Dumper d) {
        if (containedStatements.isEmpty()) {
            if (isIndenting()) {
                d.print("{}\n");
            } else {
                d.print("\n");
            }
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

    @Override
    public boolean isEffectivelyNOP() {
        for (Op04StructuredStatement statement : containedStatements) {
            if (!statement.getStatement().isEffectivelyNOP()) return false;
        }
        return true;
    }
}
