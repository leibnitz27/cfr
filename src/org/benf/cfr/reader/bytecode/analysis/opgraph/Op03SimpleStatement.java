package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.Cleaner;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.CompareByIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.TypeFilter;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CaseStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.JumpingStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.SwitchStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.WhileStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.CreationCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentAndAliasCondenser;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollectorSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollectorSimpleRW;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdent;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.collections.UniqueSeenQueue;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Op03SimpleStatement implements MutableGraph<Op03SimpleStatement>, Dumpable, StatementContainer<Statement>, IndexedStatement {
    private final List<Op03SimpleStatement> sources = ListFactory.newList();
    private final List<Op03SimpleStatement> targets = ListFactory.newList();

    private Op03SimpleStatement linearlyPrevious;
    private Op03SimpleStatement linearlyNext;

    private boolean isNop;
    private InstrIndex index;
    private Statement containedStatement;
    private SSAIdentifiers<LValue> ssaIdentifiers;
    // 
    // This statement triggers a block
    //
    private BlockIdentifier thisComparisonBlock;
    // 
    // This statement is the first in this block
    //
    private BlockIdentifier firstStatementInThisBlock;
    //
    // This statement is CONTAINED in the following blocks.
    //
    private final Set<BlockIdentifier> containedInBlocks = SetFactory.newSet();

    public Op03SimpleStatement(Op02WithProcessedDataAndRefs original, Statement statement) {
        this.containedStatement = statement;
        this.isNop = false;
        this.index = original.getIndex();
        this.ssaIdentifiers = new SSAIdentifiers<LValue>();
        this.containedInBlocks.addAll(original.getContainedInTheseBlocks());
        statement.setContainer(this);
    }

    public Op03SimpleStatement(Set<BlockIdentifier> containedIn, Statement statement, InstrIndex index) {
        this.containedStatement = statement;
        this.isNop = false;
        this.index = index;
        this.ssaIdentifiers = new SSAIdentifiers<LValue>();
        this.containedInBlocks.addAll(containedIn);
        statement.setContainer(this);
    }

    public Op03SimpleStatement(Set<BlockIdentifier> containedIn, Statement statement, SSAIdentifiers<LValue> ssaIdentifiers, InstrIndex index) {
        this.containedStatement = statement;
        this.isNop = false;
        this.index = index;
        this.ssaIdentifiers = new SSAIdentifiers<LValue>(ssaIdentifiers);
        this.containedInBlocks.addAll(containedIn);
        statement.setContainer(this);
    }


    @Override
    public List<Op03SimpleStatement> getSources() {
        return sources;
    }

    @Override
    public List<Op03SimpleStatement> getTargets() {
        return targets;
    }

    public void setLinearlyNext(Op03SimpleStatement linearlyNext) {
        this.linearlyNext = linearlyNext;
    }

    public Op03SimpleStatement getLinearlyPrevious() {
        return linearlyPrevious;
    }

    public void setLinearlyPrevious(Op03SimpleStatement linearlyPrevious) {
        this.linearlyPrevious = linearlyPrevious;
    }

    public BlockIdentifier getFirstStatementInThisBlock() {
        return firstStatementInThisBlock;
    }

    public void setFirstStatementInThisBlock(BlockIdentifier firstStatementInThisBlock) {
        this.firstStatementInThisBlock = firstStatementInThisBlock;
    }

    @Override
    public void addSource(Op03SimpleStatement source) {
        if (source == null) throw new ConfusedCFRException("Null source being added.");
        sources.add(source);
    }

    @Override
    public void addTarget(Op03SimpleStatement target) {
        targets.add(target);
    }

    @Override
    public Statement getStatement() {
        return containedStatement;
    }

    @Override
    public Statement getTargetStatement(int idx) {
        if (targets.size() <= idx) {
            throw new ConfusedCFRException("Trying to get invalid target " + idx);
        }
        Op03SimpleStatement target = targets.get(idx);
        Statement statement = target.getStatement();
        if (statement == null) throw new ConfusedCFRException("Invalid target statement");
        return statement;
    }

    @Override
    public void replaceStatement(Statement newStatement) {
        newStatement.setContainer(this);
        this.containedStatement = newStatement;
    }

    private void markAgreedNop() {
        this.isNop = true;
    }


    @Override
    public void nopOut() {
        if (this.isNop) {
            return;
            // throw new ConfusedCFRException("Trying to nopOut a node which was already nopped.");
        }
        if (this.targets.isEmpty()) {
            for (Op03SimpleStatement source : this.sources) {
                source.removeTarget(this);
            }
            this.sources.clear();
            this.containedStatement = new Nop();
            containedStatement.setContainer(this);
            markAgreedNop();
            return;
        }

        if (this.targets.size() != 1) {
            throw new ConfusedCFRException("Trying to nopOut a node with multiple targets");
        }
        this.containedStatement = new Nop();
        containedStatement.setContainer(this);
        // And, replace all parents of this with its' target.
        Op03SimpleStatement target = targets.get(0);
        for (Op03SimpleStatement source : sources) {
            source.replaceTarget(this, target);
        }
        // And replace the sources (in one go).
        target.replaceSingleSourceWith(this, sources);
        sources.clear();
        targets.clear();
        markAgreedNop();
    }

    /*
     * When nopping out a conditional, we know it has multiple targets.  We REMOVE it from all but its first target, then
     * nop it out as normal.
     * 
     * Because we know that the first target (the fall through target) is the one we're collapsing into, we replace into
     * that, and remove ALL references to the other targets.
     */
    @Override
    public void nopOutConditional() {
        this.containedStatement = new Nop();
        containedStatement.setContainer(this);
        for (int i = 1; i < targets.size(); ++i) {
            Op03SimpleStatement dropTarget = targets.get(i);
            dropTarget.removeSource(this);
        }
        // And, replace all parents of this with its' target.
        Op03SimpleStatement target = targets.get(0);
        targets.clear();
        targets.add(target);
        for (Op03SimpleStatement source : sources) {
            source.replaceTarget(this, target);
        }
        // And replace the sources (in one go).
        target.replaceSingleSourceWith(this, sources);
        sources.clear();
        targets.clear();
        markAgreedNop();
    }

    public void clear() {
        for (Op03SimpleStatement source : sources) {
            if (source.getTargets().contains(this)) {
                source.removeTarget(this);
            }
        }
        this.sources.clear();
        for (Op03SimpleStatement target : targets) {
            if (target.getSources().contains(this)) {
                target.removeSource(this);
            }
        }
        this.targets.clear();
        this.nopOut();
    }

    @Override
    public SSAIdentifiers<LValue> getSSAIdentifiers() {
        return ssaIdentifiers;
    }

    @Override
    public Set<BlockIdentifier> getBlockIdentifiers() {
        return containedInBlocks;
    }

    @Override
    public BlockIdentifier getBlockStarted() {
        return firstStatementInThisBlock;
    }

    /*
     * TODO : I think this is probably redundant (and not accurate)
     */
    @Override
    public Set<BlockIdentifier> getBlocksEnded() {
        if (linearlyPrevious == null) return SetFactory.newSet();
        Set<BlockIdentifier> in = SetFactory.newSet(linearlyPrevious.getBlockIdentifiers());
        in.removeAll(getBlockIdentifiers());
        Iterator<BlockIdentifier> iterator = in.iterator();
        while (iterator.hasNext()) {
            BlockIdentifier blockIdentifier = iterator.next();
            if (!blockIdentifier.getBlockType().isBreakable()) iterator.remove();
        }
        return in;
    }

    public Op03SimpleStatement getLinearlyNext() {
        return linearlyNext;
    }

    @Override
    public void copyBlockInformationFrom(StatementContainer<Statement> other) {
        Op03SimpleStatement other3 = (Op03SimpleStatement) other;
        this.containedInBlocks.addAll(other.getBlockIdentifiers());
        //
        // This is annoying, we only have space for one first in block.  TBH, this is a weak bit of
        // metadata, we should lose it.
        if (this.firstStatementInThisBlock == null) this.firstStatementInThisBlock = other3.firstStatementInThisBlock;
    }

    // Not just a nop, but a nop we've determined we want to remove.
    public boolean isAgreedNop() {
        return isNop;
    }

    void replaceBlockIfIn(BlockIdentifier oldB, BlockIdentifier newB) {
        if (containedInBlocks.remove(oldB)) {
            containedInBlocks.add(newB);
        }
    }

    public void splice(Op03SimpleStatement newSource) {
        if (newSource.targets.size() != 1) {
            throw new ConfusedCFRException("Can't splice (bad targets)");
        }
        if (sources.size() != 1) {
            throw new ConfusedCFRException("Can't splice (bad sources)");
        }
        if (targets.size() != 1) {
            throw new ConfusedCFRException("Can't splice (bad new target)");
        }
        Op03SimpleStatement oldSource = sources.get(0);
        Op03SimpleStatement oldTarget = targets.get(0);
        Op03SimpleStatement newTarget = newSource.targets.get(0);
        oldSource.replaceTarget(this, oldTarget);
        oldTarget.replaceSource(this, oldSource);
        newSource.replaceTarget(newTarget, this);
        newTarget.replaceSource(newSource, this);
        sources.set(0, newSource);
        targets.set(0, newTarget);
        setIndex(newSource.getIndex().justAfter());
    }

    public void replaceTarget(Op03SimpleStatement oldTarget, Op03SimpleStatement newTarget) {
        int index = targets.indexOf(oldTarget);
        if (index == -1) {
            throw new ConfusedCFRException("Invalid target");
        }
        targets.set(index, newTarget);
    }

    private void replaceSingleSourceWith(Op03SimpleStatement oldSource, List<Op03SimpleStatement> newSources) {
        if (!sources.remove(oldSource)) {
            throw new ConfusedCFRException("Invalid source");
        }
        sources.addAll(newSources);
    }

    public void replaceSource(Op03SimpleStatement oldSource, Op03SimpleStatement newSource) {
        int index = sources.indexOf(oldSource);
        if (index == -1) {
            throw new ConfusedCFRException("Invalid source");
        }
        sources.set(index, newSource);
    }

    public void removeSource(Op03SimpleStatement oldSource) {
        if (!sources.remove(oldSource)) {
            throw new ConfusedCFRException("Invalid source, tried to remove " + oldSource + "\nfrom " + this + "\nbut was not a source.");
        }
    }

    public void removeTarget(Op03SimpleStatement oldTarget) {
        if (containedStatement instanceof GotoStatement) {
            throw new ConfusedCFRException("Removing goto target");
        }
        if (!targets.remove(oldTarget)) {
            throw new ConfusedCFRException("Invalid target, tried to remove " + oldTarget + "\nfrom " + this + "\nbut was not a target.");
        }
    }

    public void removeGotoTarget(Op03SimpleStatement oldTarget) {
        if (!targets.remove(oldTarget)) {
            throw new ConfusedCFRException("Invalid target, tried to remove " + oldTarget + "\nfrom " + this + "\nbut was not a target.");
        }
    }

    @Override
    public InstrIndex getIndex() {
        return index;
    }

    public void setIndex(InstrIndex index) {
        this.index = index;
    }

    public BlockIdentifier getThisComparisonBlock() {
        return thisComparisonBlock;
    }

    public void clearThisComparisonBlock() {
        thisComparisonBlock = null;
    }

    /*
     * TODO : This is gross.
     */
    public void markBlockStatement(BlockIdentifier blockIdentifier, Op03SimpleStatement lastInBlock, Op03SimpleStatement blockEnd, List<Op03SimpleStatement> statements) {
        if (thisComparisonBlock != null) {
            throw new ConfusedCFRException("Statement marked as the start of multiple blocks");
        }
        this.thisComparisonBlock = blockIdentifier;
        switch (blockIdentifier.getBlockType()) {
            case WHILELOOP: {
                IfStatement ifStatement = (IfStatement) containedStatement;
                ifStatement.replaceWithWhileLoopStart(blockIdentifier);
                Op03SimpleStatement whileEndTarget = targets.get(1);
                // If the while statement's 'not taken' is a back jump, we normalise
                // to a forward jump to after the block, and THAT gets to be the back jump.
                // Note that this can't be done before "Remove pointless jumps".
                // The blocks that this new statement is in are the same as my blocks, barring
                // blockIdentifier.
                boolean pullOutJump = index.isBackJumpTo(whileEndTarget);
                if (!pullOutJump) {
                    // OR, if it's a forward jump, but to AFTER the end of the block
                    // TODO : ORDERCHEAT.
                    if (statements.indexOf(lastInBlock) != statements.indexOf(blockEnd) - 1) {
                        pullOutJump = true;
                    }
                }
                if (pullOutJump) {
                    Set<BlockIdentifier> backJumpContainedIn = SetFactory.newSet(containedInBlocks);
                    backJumpContainedIn.remove(blockIdentifier);
                    Op03SimpleStatement backJump = new Op03SimpleStatement(backJumpContainedIn, new GotoStatement(), blockEnd.index.justBefore());
                    whileEndTarget.replaceSource(this, backJump);
                    replaceTarget(whileEndTarget, backJump);
                    backJump.addSource(this);
                    backJump.addTarget(whileEndTarget);
                    // We have to manipulate the statement list immediately, as we're relying on spatial locality elsewhere.
                    // However, we can't just add infront of blockend naively, as there may be multiple blocks doing this.
                    // We have to add after the last statement infront of blockend which is contained in all of containedInBlocks.
                    int insertAfter = statements.indexOf(blockEnd) - 1;
                    while (!statements.get(insertAfter).containedInBlocks.containsAll(containedInBlocks)) {
                        insertAfter--;
                    }
                    backJump.index = statements.get(insertAfter).index.justAfter();
                    statements.add(insertAfter + 1, backJump);
                }
                break;
            }
            case UNCONDITIONALDOLOOP: {
                containedStatement.getContainer().replaceStatement(new WhileStatement(null, blockIdentifier));
                break;
            }
            case DOLOOP: {
                IfStatement ifStatement = (IfStatement) containedStatement;
                ifStatement.replaceWithWhileLoopEnd(blockIdentifier);
                break;
            }
            case SIMPLE_IF_ELSE:
            case SIMPLE_IF_TAKEN:
                throw new ConfusedCFRException("Shouldn't be marking the comparison of an IF");
            default:
                throw new ConfusedCFRException("Don't know how to start a block like this");
        }
    }

    public void markFirstStatementInBlock(BlockIdentifier blockIdentifier) {
        if (this.firstStatementInThisBlock != null && this.firstStatementInThisBlock != blockIdentifier
                && blockIdentifier != null) {
            throw new ConfusedCFRException("Statement already marked as first in another block");
        }
        this.firstStatementInThisBlock = blockIdentifier;
    }

    public void markBlock(BlockIdentifier blockIdentifier) {
        containedInBlocks.add(blockIdentifier);
    }

    public void collect(LValueAssignmentAndAliasCondenser lValueAssigmentCollector) {
        containedStatement.collectLValueAssignments(lValueAssigmentCollector);
    }

    public void condense(LValueRewriter lValueRewriter) {
        containedStatement.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers);
    }

    public void rewrite(ExpressionRewriter expressionRewriter) {
        containedStatement.rewriteExpressions(expressionRewriter, ssaIdentifiers);
    }

    public void findCreation(CreationCollector creationCollector) {
        containedStatement.collectObjectCreation(creationCollector);
    }

    public void clearTargets() {
        targets.clear();
    }

    public class GraphVisitorCallee implements BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>> {
        private final List<Op03SimpleStatement> reachableNodes;

        GraphVisitorCallee(List<Op03SimpleStatement> reachableNodes) {
            this.reachableNodes = reachableNodes;
        }

        @Override
        public void call(Op03SimpleStatement node, GraphVisitor<Op03SimpleStatement> graphVisitor) {
            reachableNodes.add(node);
            for (Op03SimpleStatement target : node.targets) {
                graphVisitor.enqueue(target);
            }
        }
    }

    private boolean needsLabel() {
        if (sources.size() > 1) return true;
        if (sources.size() == 0) return false;
        Op03SimpleStatement source = sources.get(0);
        return (!source.getIndex().directlyPreceeds(this.getIndex()));
    }

    @Override
    public String getLabel() {
        return getIndex().toString();
    }

    public void dumpInner(Dumper dumper) {
        if (needsLabel()) dumper.print(getLabel() + ":").newln();
        for (BlockIdentifier blockIdentifier : containedInBlocks) {
            dumper.print(blockIdentifier + " ");
        }
        getStatement().dump(dumper);
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("**********").newln();
        List<Op03SimpleStatement> reachableNodes = ListFactory.newList();
        GraphVisitorCallee graphVisitorCallee = new GraphVisitorCallee(reachableNodes);
        GraphVisitor<Op03SimpleStatement> visitor = new GraphVisitorDFS<Op03SimpleStatement>(this, graphVisitorCallee);
        visitor.process();

        try {
            Collections.sort(reachableNodes, new CompareByIndex());
        } catch (ConfusedCFRException e) {
            dumper.print("CONFUSED!" + e);
        }
        for (Op03SimpleStatement op : reachableNodes) {
            op.dumpInner(dumper);
        }
        dumper.print("**********").newln();
        return dumper;
    }

    private Op04StructuredStatement getStructuredStatementPlaceHolder() {
        return new Op04StructuredStatement(
                index,
                containedInBlocks,
                containedStatement.getStructuredStatement());
    }

    public boolean isCompound() {
        return containedStatement.isCompound();
    }

    public List<Op03SimpleStatement> splitCompound() {
        List<Op03SimpleStatement> result = ListFactory.newList();
        List<Statement> innerStatements = containedStatement.getCompoundParts();
        InstrIndex nextIndex = index.justAfter();
        for (Statement statement : innerStatements) {
            result.add(new Op03SimpleStatement(containedInBlocks, statement, nextIndex));
            nextIndex = nextIndex.justAfter();
        }
        result.get(0).firstStatementInThisBlock = firstStatementInThisBlock;
        Op03SimpleStatement previous = null;
        for (Op03SimpleStatement statement : result) {
            if (previous != null) {
                statement.addSource(previous);
                previous.addTarget(statement);
            }
            previous = statement;
        }
        Op03SimpleStatement newStart = result.get(0);
        Op03SimpleStatement newEnd = previous;
        for (Op03SimpleStatement source : sources) {
            source.replaceTarget(this, newStart);
            newStart.addSource(source);
        }
        for (Op03SimpleStatement target : targets) {
            target.replaceSource(this, newEnd);
            newEnd.addTarget(target);
        }
        this.containedStatement = new Nop();
        this.sources.clear();
        this.targets.clear();
        markAgreedNop();
        return result;
    }

    private void collectLocallyMutatedVariables(SSAIdentifierFactory<LValue, ?> ssaIdentifierFactory) {
        this.ssaIdentifiers = containedStatement.collectLocallyMutatedVariables(ssaIdentifierFactory);
    }

    public void forceSSAIdentifiers(SSAIdentifiers<LValue> newIdentifiers) {
        this.ssaIdentifiers = newIdentifiers;
    }

    public static void noteInterestingLifetimes(List<Op03SimpleStatement> statements) {

        List<Op03SimpleStatement> wantsHint = ListFactory.newList();

        Set<LValue> wanted = SetFactory.newSet();
        for (Op03SimpleStatement stm : statements) {
            Set<LValue> hints = stm.getStatement().wantsLifetimeHint();
            if (hints == null) continue;
            wantsHint.add(stm);
            wanted.addAll(hints);
        }
        if (wanted.isEmpty()) return;

        class RemoveState {
            private Set<LValue> write;
            private Set<LValue> read;
        }

        Map<Op03SimpleStatement, RemoveState> state = MapFactory.newIdentityMap();

        for (Op03SimpleStatement stm : statements) {
            LValueUsageCollectorSimpleRW rw = new LValueUsageCollectorSimpleRW();
            stm.getStatement().collectLValueUsage(rw);
            // we don't collect in enough detail to know if the read occurred before or after the write, but that's
            // ok.
            // i.e. a=a+b vs b = (a=b)+a
            // If there's a write without a read, we can propagate that information back to the last points lvalue was
            // read. (by removing SSA ident information altogether from that block.)
            // we can only do this if we are able to remove it for all children.
            Set<LValue> writes = rw.getWritten();
            Set<LValue> reads = rw.getRead();
            writes.retainAll(wanted);
            reads.retainAll(wanted);
            writes.removeAll(reads);

            RemoveState r = new RemoveState();
            r.write = writes;
            r.read = reads;

            state.put(stm, r);
        }

        List<Op03SimpleStatement> endpoints = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getTargets().isEmpty();
            }
        });
        UniqueSeenQueue<Op03SimpleStatement> toProcess = new UniqueSeenQueue<Op03SimpleStatement>(endpoints);

        while (!toProcess.isEmpty()) {
            Op03SimpleStatement node = toProcess.removeFirst();
            RemoveState r = state.get(node);
            Set<LValue> tmp = SetFactory.newSet();
            // Strictly speaking, all exception handlers that can see this are 'targets'.
            // However, this doesn't break any current usages ;)
            for (Op03SimpleStatement target : node.targets) {
                // if we (unambiguously) wrote it here, we don't need to take read from child.
                tmp.addAll(state.get(target).read);
            }
            tmp.removeAll(r.write);
            boolean changed = r.read.addAll(tmp);
            boolean addOnlyIfUnseen = !changed;
            // This changed, so we need to reprocess sources.
            for (Op03SimpleStatement source : node.sources) {
                toProcess.add(source, addOnlyIfUnseen);
            }
        }

        for (Op03SimpleStatement hint : wantsHint) {
            Set<LValue> lvs = hint.getStatement().wantsLifetimeHint();
            for (LValue lv : lvs) {
                boolean usedInChildren = false;
                for (Op03SimpleStatement target : hint.getTargets()) {
                    if (state.get(target).read.contains(lv)) {
                        usedInChildren = true;
                        break;
                    }
                }
                hint.getStatement().setLifetimeHint(lv, usedInChildren);
            }
        }
    }

    /*
     * FIXME - the problem here is that LValues COULD be mutable.  FieldValue /is/ mutable.
     *
     * Therefore we can't keep it as a key!!!!!!
     */
    public static void assignSSAIdentifiers(Method method, List<Op03SimpleStatement> statements) {

        SSAIdentifierFactory<LValue,Void> ssaIdentifierFactory = new SSAIdentifierFactory<LValue,Void>(null);

        List<LocalVariable> params = method.getMethodPrototype().getComputedParameters();
        Map<LValue, SSAIdent> initialSSAValues = MapFactory.newMap();
        for (LocalVariable param : params) {
            initialSSAValues.put(param, ssaIdentifierFactory.getIdent(param));
        }
        SSAIdentifiers<LValue> initialIdents = new SSAIdentifiers<LValue>(initialSSAValues);

        for (Op03SimpleStatement statement : statements) {
            statement.collectLocallyMutatedVariables(ssaIdentifierFactory);
        }

        Op03SimpleStatement entry = statements.get(0);

        LinkedList<Op03SimpleStatement> toProcess = ListFactory.newLinkedList();
        toProcess.addAll(statements);
        while (!toProcess.isEmpty()) {
            Op03SimpleStatement statement = toProcess.remove();
            SSAIdentifiers<LValue> ssaIdentifiers = statement.ssaIdentifiers;
            boolean changed = false;
            if (statement == entry) {
                if (ssaIdentifiers.mergeWith(initialIdents)) changed = true;
            }
            for (Op03SimpleStatement source : statement.getSources()) {
                if (ssaIdentifiers.mergeWith(source.ssaIdentifiers)) changed = true;
            }
            // If anything's changed, we need to check this statements children.
            if (changed) {
                toProcess.addAll(statement.getTargets());
            }
        }
    }

    public static Op04StructuredStatement createInitialStructuredBlock(List<Op03SimpleStatement> statements) {
        final GraphConversionHelper<Op03SimpleStatement, Op04StructuredStatement> conversionHelper = new GraphConversionHelper<Op03SimpleStatement, Op04StructuredStatement>();
        List<Op04StructuredStatement> containers = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            Op04StructuredStatement unstructuredStatement = statement.getStructuredStatementPlaceHolder();
            containers.add(unstructuredStatement);
            conversionHelper.registerOriginalAndNew(statement, unstructuredStatement);
        }
        conversionHelper.patchUpRelations();

        /* Given that we've got a linear list of statements, we want to turn them into a set of nested blocks.
         * We've already labelled statements with the list of blocks they're in, so we now need to create a partial ordering
         */
        return Op04StructuredStatement.buildNestedBlocks(containers);
    }

    public JumpType getJumpType() {
        if (containedStatement instanceof JumpingStatement) {
            return ((JumpingStatement) containedStatement).getJumpType();
        }
        return JumpType.NONE;
    }

    private Set<BlockIdentifier> possibleExitsFor = null;

    public void addPossibleExitFor(BlockIdentifier ident) {
        if (possibleExitsFor == null) possibleExitsFor = SetFactory.newOrderedSet();
        possibleExitsFor.add(ident);
    }

    public boolean isPossibleExitFor(BlockIdentifier ident) {
        return possibleExitsFor != null && possibleExitsFor.contains(ident);
    }

    private static void removePointlessSwitchDefault(Op03SimpleStatement swtch) {
        SwitchStatement switchStatement = (SwitchStatement) swtch.getStatement();
        BlockIdentifier switchBlock = switchStatement.getSwitchBlock();
        // If one of the targets is a "default", and it's definitely a target for this switch statement...
        // AND it hasn't been marked as belonging to the block, remove it.
        // A default with no code is of course equivalent to no default.
        if (swtch.getTargets().size() <= 1) return;
        for (Op03SimpleStatement tgt : swtch.getTargets()) {
            Statement statement = tgt.getStatement();
            if (statement instanceof CaseStatement) {
                CaseStatement caseStatement = (CaseStatement) statement;
                if (caseStatement.getSwitchBlock() == switchBlock) {
                    if (caseStatement.isDefault()) {
                        if (tgt.targets.size() != 1) return;
                        Op03SimpleStatement afterTgt = tgt.targets.get(0);
                        if (!afterTgt.containedInBlocks.contains(switchBlock)) {
                            // We can remove this.
                            tgt.nopOut();
                            return;
                        }
                        // If the default contains a single statement which is a
                        // break to the succeeding one, we can also remove it.
                        if (afterTgt.getStatement().getClass() != GotoStatement.class
                            || afterTgt.linearlyPrevious != tgt
                            || afterTgt.getSources().size() != 1) {
                            return;
                        }
                        if (afterTgt.linearlyNext == afterTgt.targets.get(0)) {
                            tgt.nopOut();
                            afterTgt.nopOut();
                            return;
                        }
                        return;
                    }
                }
            }
        }
    }

    public static void removePointlessSwitchDefaults(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> switches = Functional.filter(statements, new TypeFilter<SwitchStatement>(SwitchStatement.class));
        if (switches.isEmpty()) return;
        Cleaner.reLinkInPlace(statements);

        for (Op03SimpleStatement swtch : switches) {
            removePointlessSwitchDefault(swtch);
        }
    }

    @Override
    public String toString() {
        Set<Integer> blockIds = SetFactory.newSet();
        for (BlockIdentifier b : containedInBlocks) {
            blockIds.add(b.getIndex());
        }
        return "" + blockIds + " " + index + " : " + containedStatement;
    }

}
