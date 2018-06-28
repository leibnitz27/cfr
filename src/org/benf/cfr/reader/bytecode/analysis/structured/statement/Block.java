package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchIterator;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.MatchResultCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.Optional;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * TODO : Block implements way more functionality than it should - move into callers.
 */
public class Block extends AbstractStructuredStatement {
    private LinkedList<Op04StructuredStatement> containedStatements;
    private boolean indenting;
    private BlockIdentifier blockIdentifier;

    private final static LinkedList<Op04StructuredStatement> emptyBlockStatements = ListFactory.newLinkedList();

    public Block(Op04StructuredStatement statement) {
        LinkedList<Op04StructuredStatement> stm = new LinkedList<Op04StructuredStatement>();
        stm.add(statement);
        this.containedStatements = stm;
        this.indenting = false;
        this.blockIdentifier = null;
    }

    public Block(LinkedList<Op04StructuredStatement> containedStatements, boolean indenting) {
        this(containedStatements, indenting, null);
    }

    public Block(LinkedList<Op04StructuredStatement> containedStatements, boolean indenting, BlockIdentifier blockIdentifier) {
        this.containedStatements = containedStatements;
        this.indenting = indenting;
        this.blockIdentifier = blockIdentifier;
    }

    public void addStatement(Op04StructuredStatement stm) {
        if (containedStatements == emptyBlockStatements) {
            containedStatements = new LinkedList<Op04StructuredStatement>();
        }
        containedStatements.add(stm);
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

    // Super gross.
    public Pair<Boolean, Op04StructuredStatement> getOneStatementIfPresent() {
        Op04StructuredStatement res = null;
        for (Op04StructuredStatement statement : containedStatements) {
            if (!(statement.getStatement() instanceof StructuredComment)) {
                if (res == null) {
                    res = statement;
                } else {
                    return Pair.make(Boolean.FALSE, null);
                }
            }
        }
        return Pair.make(res==null, res);
    }

    public Optional<Op04StructuredStatement> getMaybeJustOneStatement() {
        Pair<Boolean, Op04StructuredStatement> tmp = getOneStatementIfPresent();
        return tmp.getSecond() == null ? Optional.<Op04StructuredStatement>empty() : Optional.of(tmp.getSecond());
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

    public void extractLabelledBlocks() {
        Iterator<Op04StructuredStatement> iterator = containedStatements.descendingIterator();
        List<Op04StructuredStatement> newEntries = ListFactory.newList();
        while (iterator.hasNext()) {
            Op04StructuredStatement stm = iterator.next();
            StructuredStatement statement = stm.getStatement();
            if (statement.getClass() == UnstructuredAnonBreakTarget.class) {
                UnstructuredAnonBreakTarget breakTarget = (UnstructuredAnonBreakTarget) statement;
                BlockIdentifier blockIdentifier = breakTarget.getBlockIdentifier();
                /*
                 *
                 */
                LinkedList<Op04StructuredStatement> inner = ListFactory.newLinkedList();
                iterator.remove();
                while (iterator.hasNext()) {
                    inner.addFirst(iterator.next());
                    iterator.remove();
                }
                Block nested = new Block(inner, true, blockIdentifier);
                Set<BlockIdentifier> outerIdents = getContainer().getBlockIdentifiers();
                Set<BlockIdentifier> innerIdents = SetFactory.newSet(outerIdents);
                innerIdents.add(blockIdentifier);
                InstrIndex newIdx = getContainer().getIndex().justAfter();
                Op04StructuredStatement newStm = new Op04StructuredStatement(
                        newIdx,
                        innerIdents,
                        nested
                );
                newEntries.add(newStm);

                List<Op04StructuredStatement> sources = stm.getSources();
                /*
                 * Any source which is an unstructured break to this block, replace with a structured labelled break.
                 */
                for (Op04StructuredStatement source : sources) {
                    StructuredStatement maybeBreak = source.getStatement();
                    // TODO : FIXME.
                    if (maybeBreak.getClass() == StructuredIf.class) {
                        // TODO:  This is due to us having originally had an ifExiting - should have rewritten.
                        StructuredIf structuredIf = (StructuredIf) maybeBreak;
                        source = structuredIf.getIfTaken();
                        maybeBreak = source.getStatement();
                    }
                    if (maybeBreak.getClass() == UnstructuredAnonymousBreak.class) {
                        UnstructuredAnonymousBreak unstructuredBreak = (UnstructuredAnonymousBreak) maybeBreak;
                        source.replaceStatement(unstructuredBreak.tryExplicitlyPlaceInBlock(blockIdentifier));
                    }
                }
                // It's not fatal if we've messed up here, we'll leave some extra block labels in...
                // But be paranoid.
                stm.replaceStatement(new StructuredComment(""));
            }
        }
        for (Op04StructuredStatement entry : newEntries) {
            containedStatements.addFirst(entry);
        }
    }

    public void combineTryCatch() {

        Set<Class<?>> skipThese = SetFactory.<Class<?>>newSet(
                StructuredCatch.class,
                StructuredFinally.class,
                StructuredTry.class,
                UnstructuredTry.class);

        int size = containedStatements.size();
        boolean finished = false;
        mainloop:
        for (int x = 0; x < size && !finished; ++x) {
            Op04StructuredStatement statement = containedStatements.get(x);
            StructuredStatement innerStatement = statement.getStatement();
            // If we've got a try statement which has no body (!), we will be left with
            // an unstructured try.  As such, if the NEXT statement is a catch or finally
            // for THIS unstructured try, structure it here.
            if (innerStatement instanceof UnstructuredTry) {
                UnstructuredTry unstructuredTry = (UnstructuredTry) innerStatement;
                if (x < (size - 1)) {
                    StructuredStatement nextStatement = containedStatements.get(x + 1).getStatement();
                    if (nextStatement instanceof StructuredCatch ||
                            nextStatement instanceof StructuredFinally) {
                        Op04StructuredStatement replacement = new Op04StructuredStatement(unstructuredTry.getEmptyTry());
                        Op04StructuredStatement.replaceInTargets(statement, replacement);
                        Op04StructuredStatement.replaceInSources(statement, replacement);
                        statement = replacement;
                        containedStatements.set(x, statement);
                        innerStatement = statement.getStatement();
                    }
                }
            }
            if (innerStatement instanceof StructuredTry) {
                StructuredTry structuredTry = (StructuredTry) innerStatement;
                BlockIdentifier tryBlockIdent = structuredTry.getTryBlockIdentifier();
                ++x;
                Op04StructuredStatement next = x < size ? containedStatements.get(x) : null;

                /*
                 * If the next statement's NOT a catch, we've got a dangling catch.
                 * Fast forward to the next catch, IF it's one for this block.
                 */
                if (next != null) {
                    StructuredStatement nextStatement = next.getStatement();
                    if (!skipThese.contains(nextStatement.getClass())) {
                        for (int y = x + 1; y < size; ++y) {
                            StructuredStatement test = containedStatements.get(y).getStatement();
                            if (test instanceof StructuredTry ||
                                    test instanceof UnstructuredTry) {
                                continue mainloop;
                            }
                            if (test instanceof StructuredCatch) {
                                Set<BlockIdentifier> blocks = ((StructuredCatch) test).getPossibleTryBlocks();
                                if (blocks.contains(tryBlockIdent)) {
                                    x = y;
                                    next = containedStatements.get(y);
                                    break;
                                }
                            }
                        }
                    }
                }

                while (x < size && next != null) {
                    ++x;
                    StructuredStatement nextStatement = next.getStatement();
                    if (nextStatement instanceof StructuredComment) {
                        next.nopThis(); // pointless.
                        // Nothing.
                    } else if (nextStatement instanceof StructuredCatch) {
                        Set<BlockIdentifier> blocks = ((StructuredCatch) nextStatement).getPossibleTryBlocks();
                        if (!blocks.contains(tryBlockIdent)) {
                            --x;
                            break;
                        }
                        structuredTry.addCatch(next.nopThisAndReplace());
                        if (x < size) {
                            next = containedStatements.get(x);
                        } else {
                            // We'll have to find some other way of getting the next statement, probably need a DFS :(
                            next = null;
                            finished = true;
                        }
                    } else if (next.getStatement() instanceof StructuredFinally) {
                        structuredTry.setFinally(next.nopThisAndReplace());
                        if (x < size) {
                            next = containedStatements.get(x);
                        } else {
                            // We'll have to find some other way of getting the next statement, probably need a DFS :(
                            next = null;
                            finished = true;
                        }
                    } else {
                        --x;
                        break;
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
    public boolean isScopeBlock() {
        return true;
    }

    @Override
    public void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope) {
        for (int x = 0, len = containedStatements.size(); x < len; ++x) {
            Op04StructuredStatement structuredBlock = containedStatements.get(x);
            scope.setNextAtThisLevel(this, x < len - 1 ? x + 1 : -1);
            structuredBlock.transform(transformer, scope);
        }
    }

    @Override
    public void transformStructuredChildrenInReverse(StructuredStatementTransformer transformer, StructuredScope scope) {
        int last = containedStatements.size() - 1;
        for (int x = last; x>=0; --x) {
            Op04StructuredStatement structuredBlock = containedStatements.get(x);
            scope.setNextAtThisLevel(this, x < last ? x + 1 : -1);
            structuredBlock.transform(transformer, scope);
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
    public BlockIdentifier getBreakableBlockOrNull() {
        return (blockIdentifier != null && blockIdentifier.hasForeignReferences()) ? blockIdentifier : null;
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
            scopeDiscoverer.mark(item);
            item.traceLocalVariableScope(scopeDiscoverer);
        }
        scopeDiscoverer.leaveBlock(this);
    }

    /*
     * This variable has been defined in an ENCLOSED scope, but used at this level.
     */
    @Override
    public void markCreator(LValue scopedEntity, StatementContainer<StructuredStatement> hint) {
        Op04StructuredStatement declaration = new Op04StructuredStatement(new StructuredDefinition(scopedEntity));
        if (hint != null) {
            int idx = containedStatements.indexOf(hint);
            if (idx != -1) {
                containedStatements.add(idx, declaration);
                return;
            }
        }
        containedStatements.addFirst(declaration);
    }

    @Override
    public boolean alwaysDefines(LValue scopedEntity) {
        return false;
    }

    @Override
    public Dumper dump(Dumper d) {
        boolean isIndenting = isIndenting();
        if (blockIdentifier != null) {
            if (blockIdentifier.hasForeignReferences()) {
                d.print(blockIdentifier.getName() + " : ");
                isIndenting = true;
            } else {
                isIndenting = false;
            }
        }
        if (containedStatements.isEmpty()) {
            if (isIndenting) {
                d.print("{}\n");
            } else {
                d.print("\n");
            }
            return d;
        }
        try {
            if (isIndenting) {
                d.print("{\n");
                d.indent(1);
            }
            for (Op04StructuredStatement structuredBlock : containedStatements) {
                structuredBlock.dump(d);
            }
        } finally {
            if (isIndenting) {
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

    public void setIndenting(boolean indenting) {
        this.indenting = indenting;
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
