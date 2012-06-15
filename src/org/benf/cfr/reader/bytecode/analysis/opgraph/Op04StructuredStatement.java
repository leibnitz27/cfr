package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
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

    private Set<BlockIdentifier> blockMembership;

    private static final Set<BlockIdentifier> EMPTY_BLOCKSET = SetFactory.newSet();

    private static Set<BlockIdentifier> blockSet(List<BlockIdentifier> in) {
        if (in == null || in.isEmpty()) return EMPTY_BLOCKSET;
        return SetFactory.newSet(in);
    }

    public Op04StructuredStatement(
            StructuredStatement justStatement
    ) {
        this.structuredStatement = justStatement;
        this.instrIndex = new InstrIndex(-1000);
        this.blockMembership = EMPTY_BLOCKSET;
    }

    public Op04StructuredStatement(
            InstrIndex instrIndex,
            List<BlockIdentifier> blockMembership,
            StructuredStatement structuredStatement) {
        this.instrIndex = instrIndex;
        this.structuredStatement = structuredStatement;
        this.blockMembership = blockSet(blockMembership);
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
            dumper.printLabel(instrIndex.toString() + ": // " + sources.size() + " sources");
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

    public void removeLastContinue(BlockIdentifier block) {
        if (structuredStatement instanceof Block) {
            boolean removed = ((Block) structuredStatement).removeLastContinue(block);
            System.out.println("Removing last continue for " + block + " succeeded? " + removed);
        } else {
            throw new ConfusedCFRException("Trying to remove last continue, but statement isn't block");
        }
    }

    public void removeLastGoto() {
        if (structuredStatement instanceof Block) {
            ((Block) structuredStatement).removeLastGoto();
        } else {
            throw new ConfusedCFRException("Trying to remove last goto, but statement isn't a block!");
        }
    }

    @Override
    public String toString() {
        return "OP4:" + structuredStatement;
    }

    public void replaceStatementWithNOP(String comment) {
        this.structuredStatement = new StructuredComment(comment);
    }

    private boolean claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier thisBlock) {
        int idx = targets.indexOf(innerBlock);
        if (idx == -1) return false;
        StructuredStatement replacement = structuredStatement.claimBlock(innerBlock, thisBlock);
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
     * This is pretty inefficient....
     */
    private static Set<BlockIdentifier> getEndingBlocks(Set<BlockIdentifier> wasIn, Set<BlockIdentifier> nowIn) {
        Set<BlockIdentifier> wasCopy = SetFactory.newSet(wasIn);
        wasCopy.removeAll(nowIn);
//        System.out.println("From " + wasIn + " to " + nowIn + " = " + wasCopy);
        return wasCopy;
    }

    private static BlockIdentifier getStartingBlocks(Set<BlockIdentifier> wasIn, Set<BlockIdentifier> nowIn) {
        /* 
         * We /KNOW/ that we've already checked and dealt with blocks we've left.
         * So we're only entering a new block if |nowIn|>|wasIn|.
         */
        if (nowIn.size() <= wasIn.size()) return null;
        Set<BlockIdentifier> nowCopy = SetFactory.newSet(nowIn);
        nowCopy.removeAll(wasIn);
        if (nowCopy.size() != 1) {
            System.out.println("From " + wasIn + " to " + nowIn + " = " + nowCopy);
            throw new RuntimeException("Started " + nowCopy.size() + " blocks at once");
        }
        return nowCopy.iterator().next();
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


//            System.out.print(container + " starts " + container.startBlock + " in [");
//            for (BlockIdentifier blockIdentifier : container.blockMembership) {
//                System.out.print(blockIdentifier + " ");
//            }
//            System.out.println("]");


//            System.out.println("Adding " + container + " to currentBlock");

            /*
             * if this statement has the same membership as blocksCurrentlyIn, it's in the same 
             * block as the previous statement, so emit it into currentBlock.
             * 
             * If not, we end the blocks that have been left, in reverse order of arriving in them. 
             * 
             * If we've started a new block.... start that.
             */
            Set<BlockIdentifier> endOfTheseBlocks = getEndingBlocks(blocksCurrentlyIn, container.blockMembership);
            if (!endOfTheseBlocks.isEmpty()) {

                System.out.println("statement is last statement in these blocks " + endOfTheseBlocks);

                while (!endOfTheseBlocks.isEmpty()) {
                    if (currentBlockIdentifier == null) {
                        throw new ConfusedCFRException("Trying to end block, but not in any!");
                    }
                    // Leaving a block, but
                    if (!endOfTheseBlocks.remove(currentBlockIdentifier)) {
                        throw new ConfusedCFRException("Tried to end blocks " + endOfTheseBlocks + ", but top level block is " + currentBlockIdentifier);
                    }
                    blocksCurrentlyIn.remove(currentBlockIdentifier);
                    LinkedList<Op04StructuredStatement> blockJustEnded = currentBlock;
                    StackedBlock popBlock = stackedBlocks.pop();
                    currentBlock = popBlock.statements;
                    // todo : Do I still need to get /un/structured parents right?
                    Op04StructuredStatement finishedBlock = new Op04StructuredStatement(new Block(blockJustEnded, true));
                    finishedBlock.replaceAsSource(blockJustEnded.getFirst());
                    Op04StructuredStatement blockStartContainer = popBlock.outerStart;
                    if (!blockStartContainer.claimBlock(finishedBlock, currentBlockIdentifier)) {
                        currentBlock.add(finishedBlock);
                    }
                    currentBlockIdentifier = popBlock.blockIdentifier;
                }
            }

            BlockIdentifier startsThisBlock = getStartingBlocks(blocksCurrentlyIn, container.blockMembership);
            if (startsThisBlock != null) {
                System.out.println("Starting block " + startsThisBlock);
                BlockType blockType = startsThisBlock.getBlockType();
                // A bit confusing.  StartBlock for a while loop is the test.
                // StartBlock for conditionals is the first element of the conditional.
                // I need to refactor this......
                Op04StructuredStatement blockClaimer = currentBlock.getLast();

                stackedBlocks.push(new StackedBlock(currentBlockIdentifier, currentBlock, blockClaimer));
                currentBlock = ListFactory.newLinkedList();
                currentBlockIdentifier = startsThisBlock;
                blocksCurrentlyIn.add(currentBlockIdentifier);
            }

            currentBlock.add(container);


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
