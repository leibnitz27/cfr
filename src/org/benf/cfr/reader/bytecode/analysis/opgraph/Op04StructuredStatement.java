package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.UnstructuredWhile;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.CFRState;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.LoggerFactory;

import java.util.*;
import java.util.logging.Logger;

/**
 * Created:
 * User: lee
 * Date: 14/05/2012
 * <p/>
 * Structured statements
 */
public class Op04StructuredStatement implements MutableGraph<Op04StructuredStatement>, Dumpable, StatementContainer<StructuredStatement> {
    private static final Logger logger = LoggerFactory.create(Op04StructuredStatement.class);

    private InstrIndex instrIndex;
    // Should we be bothering with sources and targets?  Not once we're "Properly" structured...
    private List<Op04StructuredStatement> sources = ListFactory.newList();
    private List<Op04StructuredStatement> targets = ListFactory.newList();
    private StructuredStatement structuredStatement;

    private Set<BlockIdentifier> blockMembership;

    private static final Set<BlockIdentifier> EMPTY_BLOCKSET = SetFactory.newSet();

    private static Set<BlockIdentifier> blockSet(Collection<BlockIdentifier> in) {
        if (in == null || in.isEmpty()) return EMPTY_BLOCKSET;
        return SetFactory.newSet(in);
    }

    public Op04StructuredStatement(
            StructuredStatement justStatement
    ) {
        this.structuredStatement = justStatement;
        this.instrIndex = new InstrIndex(-1000);
        this.blockMembership = EMPTY_BLOCKSET;
        justStatement.setContainer(this);
    }

    public Op04StructuredStatement(
            InstrIndex instrIndex,
            Collection<BlockIdentifier> blockMembership,
            StructuredStatement structuredStatement) {
        this.instrIndex = instrIndex;
        this.structuredStatement = structuredStatement;
        this.blockMembership = blockSet(blockMembership);
        structuredStatement.setContainer(this);
    }

    // TODO: This isn't quite right.  Should actually be removing the node.
    public Op04StructuredStatement nopThisAndReplace() {
        Op04StructuredStatement replacement = new Op04StructuredStatement(instrIndex, blockMembership, structuredStatement);
        replaceStatementWithNOP("");
        return replacement;
    }

    @Override
    public StructuredStatement getStatement() {
        return structuredStatement;
    }

    @Override
    public StructuredStatement getTargetStatement(int idx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLabel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InstrIndex getIndex() {
        return instrIndex;
    }

    @Override
    public void nopOut() {
        replaceStatementWithNOP("");
    }

    @Override
    public void replaceStatement(StructuredStatement newTarget) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void nopOutConditional() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSAIdentifiers getSSAIdentifiers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<BlockIdentifier> getBlockIdentifiers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BlockIdentifier getBlockStarted() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<BlockIdentifier> getBlocksEnded() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copyBlockInformationFrom(StatementContainer<StructuredStatement> other) {
        throw new UnsupportedOperationException();
    }


    private boolean hasUnstructuredSource() {
        for (Op04StructuredStatement source : sources) {
            if (!source.structuredStatement.isProperlyStructured()) {
                return true;
            }
        }
        return false;
    }


    public Collection<BlockIdentifier> getBlockMembership() {
        return blockMembership;
    }

    @Override
    public Dumper dump(Dumper dumper) {
        if (hasUnstructuredSource()) {
            dumper.printLabel(instrIndex.toString() + ": // " + sources.size() + " sources");
        }
        structuredStatement.dump(dumper);
        return dumper;
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

    public void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer) {
        structuredStatement.traceLocalVariableScope(scopeDiscoverer);
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
            throw new ConfusedCFRException("Invalid target.  Trying to replace " + from + " -> " + to);
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

    /*
     * This is called far too much for transforms - should make them work on native structures
     * where possible.
     */
    public void linearizeStatementsInto(List<StructuredStatement> out) {
        structuredStatement.linearizeInto(out);
    }

    public void removeLastContinue(BlockIdentifier block) {
        if (structuredStatement instanceof Block) {
            boolean removed = ((Block) structuredStatement).removeLastContinue(block);
            logger.info("Removing last continue for " + block + " succeeded? " + removed);
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

    public void removeLastGoto(Op04StructuredStatement toHere) {
        if (structuredStatement instanceof Block) {
            ((Block) structuredStatement).removeLastGoto(toHere);
        } else {
            throw new ConfusedCFRException("Trying to remove last goto, but statement isn't a block!");
        }
    }

    public UnstructuredWhile removeLastEndWhile() {
        if (structuredStatement instanceof Block) {
            return ((Block) structuredStatement).removeLastEndWhile();
        } else {
            return null; // Can't find.
        }
    }

    public void informBlockMembership(Vector<BlockIdentifier> currentlyIn) {
        StructuredStatement replacement = structuredStatement.informBlockHeirachy(currentlyIn);
        if (replacement == null) return;
        this.structuredStatement = replacement;
        replacement.setContainer(this);
    }

    @Override
    public String toString() {
        return "OP4:" + structuredStatement;
    }

    public void replaceStatementWithNOP(String comment) {
        this.structuredStatement = new StructuredComment(comment);
        this.structuredStatement.setContainer(this);
    }

    private boolean claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier thisBlock, Vector<BlockIdentifier> currentlyIn) {
        int idx = targets.indexOf(innerBlock);
        if (idx == -1) {
            return false;
        }
        StructuredStatement replacement = structuredStatement.claimBlock(innerBlock, thisBlock, currentlyIn);
        if (replacement == null) return false;
        this.structuredStatement = replacement;
        replacement.setContainer(this);
        return true;
    }

    public void replaceContainedStatement(StructuredStatement structuredStatement) {
        this.structuredStatement = structuredStatement;
        this.structuredStatement.setContainer(this);
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

        private static class BlockIdentifierGetter implements UnaryFunction<StackedBlock, BlockIdentifier> {
            @Override
            public BlockIdentifier invoke(StackedBlock arg) {
                return arg.blockIdentifier;
            }
        }
    }


    /*
     * This is pretty inefficient....
     */
    private static Set<BlockIdentifier> getEndingBlocks(Stack<BlockIdentifier> wasIn, Set<BlockIdentifier> nowIn) {
        Set<BlockIdentifier> wasCopy = SetFactory.newSet(wasIn);
        wasCopy.removeAll(nowIn);
        return wasCopy;
    }

    private static BlockIdentifier getStartingBlocks(Stack<BlockIdentifier> wasIn, Set<BlockIdentifier> nowIn) {
        /* 
         * We /KNOW/ that we've already checked and dealt with blocks we've left.
         * So we're only entering a new block if |nowIn|>|wasIn|.
         */
        if (nowIn.size() <= wasIn.size()) return null;
        Set<BlockIdentifier> nowCopy = SetFactory.newSet(nowIn);
        nowCopy.removeAll(wasIn);
        if (nowCopy.size() != 1) {
            logger.warning("From " + wasIn + " to " + nowIn + " = " + nowCopy);
            throw new ConfusedCFRException("Started " + nowCopy.size() + " blocks at once");
        }
        return nowCopy.iterator().next();
    }

    private static class MutableProcessingBlockState {
        BlockIdentifier currentBlockIdentifier = null;
        LinkedList<Op04StructuredStatement> currentBlock = ListFactory.newLinkedList();
    }

    public static void processEndingBlocks(
            final Set<BlockIdentifier> endOfTheseBlocks,
            final Stack<BlockIdentifier> blocksCurrentlyIn,
            final Stack<StackedBlock> stackedBlocks,
            final MutableProcessingBlockState mutableProcessingBlockState) {
        logger.fine("statement is last statement in these blocks " + endOfTheseBlocks);

        while (!endOfTheseBlocks.isEmpty()) {
            if (mutableProcessingBlockState.currentBlockIdentifier == null) {
                throw new ConfusedCFRException("Trying to end block, but not in any!");
            }
            // Leaving a block, but
            if (!endOfTheseBlocks.remove(mutableProcessingBlockState.currentBlockIdentifier)) {
                throw new ConfusedCFRException("Tried to end blocks " + endOfTheseBlocks + ", but top level block is " + mutableProcessingBlockState.currentBlockIdentifier);
            }
            BlockIdentifier popBlockIdentifier = blocksCurrentlyIn.pop();
            if (popBlockIdentifier != mutableProcessingBlockState.currentBlockIdentifier) {
                throw new ConfusedCFRException("Tried to end blocks " + endOfTheseBlocks + ", but top level block is " + mutableProcessingBlockState.currentBlockIdentifier);
            }
            LinkedList<Op04StructuredStatement> blockJustEnded = mutableProcessingBlockState.currentBlock;
            StackedBlock popBlock = stackedBlocks.pop();
            mutableProcessingBlockState.currentBlock = popBlock.statements;
            // todo : Do I still need to get /un/structured parents right?
            Op04StructuredStatement finishedBlock = new Op04StructuredStatement(new Block(blockJustEnded, true));
            finishedBlock.replaceAsSource(blockJustEnded.getFirst());
            Op04StructuredStatement blockStartContainer = popBlock.outerStart;

            logger.fine("Trying to claim with " + blockStartContainer);
            if (!blockStartContainer.claimBlock(finishedBlock, mutableProcessingBlockState.currentBlockIdentifier, blocksCurrentlyIn)) {
                mutableProcessingBlockState.currentBlock.add(finishedBlock);
            }
            mutableProcessingBlockState.currentBlockIdentifier = popBlock.blockIdentifier;
        }
    }

    public boolean isFullyStructured() {
        return structuredStatement.isRecursivelyStructured();
    }

    /*
    *
    */
    public static Op04StructuredStatement buildNestedBlocks(List<Op04StructuredStatement> containers) {
        /* 
         * the blocks we're in, and when we entered them.
         *
         * This is ugly, could keep track of this more cleanly.
         */
        Stack<BlockIdentifier> blocksCurrentlyIn = StackFactory.newStack();
        LinkedList<Op04StructuredStatement> outerBlock = ListFactory.newLinkedList();
        Stack<StackedBlock> stackedBlocks = StackFactory.newStack();

        MutableProcessingBlockState mutableProcessingBlockState = new MutableProcessingBlockState();
        mutableProcessingBlockState.currentBlock = outerBlock;

        for (Op04StructuredStatement container : containers) {
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
                processEndingBlocks(endOfTheseBlocks, blocksCurrentlyIn, stackedBlocks, mutableProcessingBlockState);
            }

            BlockIdentifier startsThisBlock = getStartingBlocks(blocksCurrentlyIn, container.blockMembership);
            if (startsThisBlock != null) {
                logger.fine("Starting block " + startsThisBlock);
                BlockType blockType = startsThisBlock.getBlockType();
                // A bit confusing.  StartBlock for a while loop is the test.
                // StartBlock for conditionals is the first element of the conditional.
                // I need to refactor this......
                Op04StructuredStatement blockClaimer = mutableProcessingBlockState.currentBlock.getLast();

                stackedBlocks.push(new StackedBlock(mutableProcessingBlockState.currentBlockIdentifier, mutableProcessingBlockState.currentBlock, blockClaimer));
                mutableProcessingBlockState.currentBlock = ListFactory.newLinkedList();
                mutableProcessingBlockState.currentBlockIdentifier = startsThisBlock;
                blocksCurrentlyIn.push(mutableProcessingBlockState.currentBlockIdentifier);
            }

            container.informBlockMembership(blocksCurrentlyIn);
            mutableProcessingBlockState.currentBlock.add(container);


        }
        /* 
         * End any blocks we're still in.
         */
        if (!stackedBlocks.isEmpty()) {
            processEndingBlocks(SetFactory.newSet(blocksCurrentlyIn), blocksCurrentlyIn, stackedBlocks, mutableProcessingBlockState);
        }
        Block result = new Block(outerBlock, true);
        return new Op04StructuredStatement(result);

    }


    private static class TryCatchTidier implements StructuredStatementTransformer {
        @Override
        public StructuredStatement transform(StructuredStatement in) {
            if (in instanceof Block) {
                // Search for try statements, see if we can combine following catch statements with them.
                Block block = (Block) in;
                block.combineTryCatch();
            }
            in.transformStructuredChildren(this);
            return in;
        }
    }

    public void transform(StructuredStatementTransformer transformer) {
        structuredStatement = transformer.transform(structuredStatement);
    }

    /*
     * mutually exclusive blocks may have trailling gotos after them.  It's hard to remove them prior to here, but now we have
     * structure, we can find them more easily.
     */

    public static void tidyTryCatch(Op04StructuredStatement root) {
        root.transform(new TryCatchTidier());
    }

    public static void removePointlessReturn(Op04StructuredStatement root) {
        StructuredStatement statement = root.getStatement();
        if (statement instanceof Block) {
            Block block = (Block) statement;
            block.removeLastNVReturn();
        }
    }

    /*
     * We've got structured (hopefully) code now, so we can find the initial unbranched assignment points
     * for any given variable.
     *
     * We can also discover if stack locations have been re-used with a type change - this would have resulted
     * in what looks like invalid variable re-use, which we can now convert.
     */
    public static void discoverVariableScopes(MethodPrototype methodPrototype, Op04StructuredStatement root) {
        LValueScopeDiscoverer scopeDiscoverer = new LValueScopeDiscoverer(methodPrototype);
        root.traceLocalVariableScope(scopeDiscoverer);
        // We should have found scopes, now update to reflect this.
        scopeDiscoverer.markDiscoveredCreations();
    }

    private static LValue removeSyntheticConstructorParam(Method method, Op04StructuredStatement root) {
        MethodPrototype prototype = method.getMethodPrototype();
        List<LocalVariable> vars = prototype.getParameters();
        LocalVariable outerThis = vars.get(0);
        // Todo : Should we test that it's the right type?  Already been done, really....
        prototype.setExplicitThisRemoval(true);

        /* Now we need to remove the usage of outerThis */
        InnerClassConstructorRewriter innerClassConstructorRewriter = new InnerClassConstructorRewriter(outerThis);
        innerClassConstructorRewriter.rewrite(root);
        LValue matchedLValue = innerClassConstructorRewriter.getMatchedLValue();
        if (matchedLValue == null) return null;
        /* If there was a value to match, we now have to replace the parameter with the member anywhere it was used
         * in the constructor.
         */
        Map<LValue, LValue> replacements = MapFactory.newMap();
        replacements.put(outerThis, matchedLValue);
        LValueReplacingRewriter lValueReplacingRewriter = new LValueReplacingRewriter(replacements);

        MiscStatementTools.applyExpressionRewriter(root, lValueReplacingRewriter);

        return matchedLValue;
    }

    /*
     * This is performed in 2 parts, only (b) is implemented here, (a) is in ConstructiorInvokation /
     * MemberFunctionInvokation (for super).
     *
     * a)
     * Remove first arg to calls which use inner <init> on non-statics.
     *
     * b)
     * Also, if we are ourselves an inner class constructor, remove the first argument, and remove usages of it
     * downstream, plus mark the synthetic outer this as invisible.
     */
    public static LValue fixInnerClassConstruction(Method method, Op04StructuredStatement root) {

        /*
         * b)
         */
        LValue res = null;
        if (method.isConstructor()) {
            ClassFile classFile = method.getClassFile();
            if (classFile.isInnerClass() && !classFile.testAccessFlag(AccessFlag.ACC_STATIC)) {
                res = removeSyntheticConstructorParam(method, root);
            }
        }
        return res;
    }

    public static void inlineSyntheticAccessors(CFRState cfrState, Method method, Op04StructuredStatement root) {
        JavaTypeInstance classType = method.getClassFile().getClassType();
        new SyntheticAccessorRewriter(cfrState, classType).rewrite(root);
    }

    public static void removeConstructorBoilerplate(Op04StructuredStatement root) {
        new RedundantSuperRewriter().rewrite(root);
    }

    public static void rewriteLambdas(CFRState cfrState, Method method, Op04StructuredStatement root) {
        if (!cfrState.rewriteLambdas()) return;

        new LambdaRewriter(method.getClassFile()).rewrite(root);
    }

    public static void removePrimitiveDeconversion(CFRState cfrState, Method method, Op04StructuredStatement root) {
        if (!cfrState.getBooleanOpt(CFRState.SUGAR_BOXING)) return;

        new PrimitiveConversionRewriter().rewrite(root);
    }

    public static void replaceNestedSyntheticOuterRefs(Op04StructuredStatement root) {
        List<StructuredStatement> statements = MiscStatementTools.linearise(root);
        //
        // It strikes me I could do this as a map replace, if I generate the set of possible rewrites.
        // probably a bit gross though ;)
        //
        SyntheticOuterRefRewriter syntheticOuterRefRewriter = new SyntheticOuterRefRewriter();
        for (StructuredStatement statement : statements) {
            statement.rewriteExpressions(syntheticOuterRefRewriter);
        }

    }
}
