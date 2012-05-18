package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.StackFactory;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Created:
 * User: lee
 * Date: 14/05/2012
 * <p/>
 * Structured statements
 */
public class Op04StructuredStatement implements MutableGraph<Op04StructuredStatement>, Dumpable {
    private InstrIndex instrIndex;
    private List<Op04StructuredStatement> sources = ListFactory.newList();
    private List<Op04StructuredStatement> targets = ListFactory.newList();
    private StructuredStatement structuredStatement;

    private BlockIdentifier startBlock;
    private Set<BlockIdentifier> blockMembership;
    private Set<BlockIdentifier> endOfTheseBlocks;

    private static final Set<BlockIdentifier> EMPTY_BLOCKSET = SetFactory.newSet();

    private static Set<BlockIdentifier> blockSet(List<BlockIdentifier> in) {
        if (in == null || in.isEmpty()) return EMPTY_BLOCKSET;
        return SetFactory.newSet(in);
    }

    public Op04StructuredStatement(
            StructuredStatement justStatement
    ) {
        this.structuredStatement = justStatement;
        this.instrIndex = null;
        this.blockMembership = EMPTY_BLOCKSET;
        this.endOfTheseBlocks = EMPTY_BLOCKSET;
    }

    public Op04StructuredStatement(
            InstrIndex instrIndex,
            BlockIdentifier startBlock,
            List<BlockIdentifier> blockMembership,
            List<BlockIdentifier> endOfTheseBlocks,
            StructuredStatement structuredStatement) {
        this.instrIndex = instrIndex;
        this.structuredStatement = structuredStatement;
        this.startBlock = startBlock;
        this.blockMembership = blockSet(blockMembership);
        this.endOfTheseBlocks = blockSet(endOfTheseBlocks);
        structuredStatement.setContainer(this);
    }

    public StructuredStatement getStructuredStatement() {
        return structuredStatement;
    }

    private boolean hasUnstructuredSource() {
        for (Op04StructuredStatement source : sources) {
            if (!source.structuredStatement.isProperlyStructured()) return true;
        }
        return false;
    }

    @Override
    public void dump(Dumper dumper) {
        if (hasUnstructuredSource()) {
            dumper.printLabel(instrIndex.toString());
        }
        structuredStatement.dump(dumper);
    }

    @Override
    public List<Op04StructuredStatement> getSources() {
        return sources;
    }

    @Override
    public List<Op04StructuredStatement> getTargets() {
        return targets;
    }

    @Override
    public void addSource(Op04StructuredStatement source) {
        sources.add(source);
    }

    @Override
    public void addTarget(Op04StructuredStatement target) {
        targets.add(target);
    }

    public String getTargetLabel(int idx) {
        return targets.get(idx).instrIndex.toString();
    }

    /* 
    * Take all nodes pointing at old, and point them at me.
    * Add an unconditional target of old.
    */
    private void replaceAsSource(Op04StructuredStatement old) {
        replaceInSources(old, this);
        this.addTarget(old);
        old.addSource(this);
    }

    public void replaceTarget(Op04StructuredStatement from, Op04StructuredStatement to) {
        int index = targets.indexOf(from);
        if (index == -1) {
            throw new ConfusedCFRException("Invalid target");
        }
        targets.set(index, to);
    }

    public void replaceSource(Op04StructuredStatement from, Op04StructuredStatement to) {
        int index = sources.indexOf(from);
        if (index == -1) {
            throw new ConfusedCFRException("Invalid source");
        }
        sources.set(index, to);
    }

    public void setSources(List<Op04StructuredStatement> sources) {
        this.sources = sources;
    }

    public void setTargets(List<Op04StructuredStatement> targets) {
        this.targets = targets;
    }

    public static void replaceInSources(Op04StructuredStatement original, Op04StructuredStatement replacement) {
        for (Op04StructuredStatement source : original.getSources()) {
            source.replaceTarget(original, replacement);
        }
        replacement.setSources(original.getSources());
        original.setSources(ListFactory.<Op04StructuredStatement>newList());
    }

    public static void replaceInTargets(Op04StructuredStatement original, Op04StructuredStatement replacement) {
        for (Op04StructuredStatement target : original.getTargets()) {
            target.replaceSource(original, replacement);
        }
        replacement.setTargets(original.getTargets());
        original.setTargets(ListFactory.<Op04StructuredStatement>newList());
    }

    private boolean startsBlock() {
        return startBlock != null;
    }

    private boolean claimBlock(Op04StructuredStatement innerBlock) {
        int idx = targets.indexOf(innerBlock);
        if (idx == -1) return false;
        StructuredStatement replacement = structuredStatement.claimBlock(innerBlock);
        if (replacement == null) return false;
        this.structuredStatement = replacement;
        replacement.setContainer(this);
        return true;
    }

    private static class StackedBlock {
        BlockIdentifier blockIdentifier;
        LinkedList<Op04StructuredStatement> statements;
        Op04StructuredStatement outerStart;

        private StackedBlock(BlockIdentifier blockIdentifier, LinkedList<Op04StructuredStatement> statements, Op04StructuredStatement outerStart) {
            this.blockIdentifier = blockIdentifier;
            this.statements = statements;
            this.outerStart = outerStart;
        }
    }

    /*
     * 
     */
    public static Op04StructuredStatement buildNestedBlocks(List<Op04StructuredStatement> containers) {
        /* 
         * the blocks we're in, and when we entered them.
         */
        Set<BlockIdentifier> blocksCurrentlyIn = SetFactory.newSet();
        BlockIdentifier currentBlockIdentifier = null;
        LinkedList<Op04StructuredStatement> outerBlock = ListFactory.newLinkedList();
        LinkedList<Op04StructuredStatement> currentBlock = outerBlock;
        Stack<StackedBlock> stackedBlocks = StackFactory.newStack();
        for (Op04StructuredStatement container : containers) {
            /* 
             * if this statement has the same membership as blocksCurrentlyIn, it's in the same 
             * block as the previous statement, so emit it into currentBlock.
             * 
             * If not, we end the blocks that have been left, in reverse order of arriving in them. 
             * 
             * If we've started a new block.... start that.
             */
            if (!container.endOfTheseBlocks.isEmpty()) {
                // Clone so we can mutate.
                Set<BlockIdentifier> endOfTheseBlocks = SetFactory.newSet(container.endOfTheseBlocks);
                while (!endOfTheseBlocks.isEmpty()) {
                    if (currentBlockIdentifier == null) {
                        throw new ConfusedCFRException("Trying to end block, but not in any!");
                    }
                    if (!endOfTheseBlocks.remove(currentBlockIdentifier)) {
                        throw new ConfusedCFRException("Tried to end block " + currentBlockIdentifier + " but am not in it!!");
                    }
                    LinkedList<Op04StructuredStatement> tmp = currentBlock;
                    StackedBlock popBlock = stackedBlocks.pop();
                    currentBlock = popBlock.statements;
                    currentBlockIdentifier = popBlock.blockIdentifier;
                    // todo : Do I still need to get /un/structured parents right?
                    Op04StructuredStatement finishedBlock = new Op04StructuredStatement(new Block(tmp, true));
                    finishedBlock.replaceAsSource(tmp.getFirst());
                    Op04StructuredStatement blockStartContainer = popBlock.outerStart;
                    if (!blockStartContainer.claimBlock(finishedBlock)) {
                        currentBlock.add(finishedBlock);
                    }
                }
            }
            currentBlock.add(container);

            if (container.startsBlock()) {
                stackedBlocks.push(new StackedBlock(currentBlockIdentifier, currentBlock, container));
                currentBlock = ListFactory.newLinkedList();
                currentBlockIdentifier = container.startBlock;
            }
        }
        /* 
         * By here, the stack should be empty, and outerblocks should be all that remains.
         */
        if (!stackedBlocks.isEmpty()) {
            throw new ConfusedCFRException("Finished processing block membership, not empty!");
        }
        Block result = new Block(outerBlock, true);
        return new Op04StructuredStatement(result);

    }

}
