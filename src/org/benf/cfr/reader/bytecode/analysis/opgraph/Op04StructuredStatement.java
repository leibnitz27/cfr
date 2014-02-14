package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.checker.Op04Checker;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationAnoynmousInner;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableFactory;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
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
public class Op04StructuredStatement implements MutableGraph<Op04StructuredStatement>, Dumpable, StatementContainer<StructuredStatement>, TypeUsageCollectable {
    private static final Logger logger = LoggerFactory.create(Op04StructuredStatement.class);

    private InstrIndex instrIndex;
    // Should we be bothering with sources and targets?  Not once we're "Properly" structured...
    private List<Op04StructuredStatement> sources = ListFactory.newList();
    private List<Op04StructuredStatement> targets = ListFactory.newList();
    private StructuredStatement structuredStatement;

    private Set<BlockIdentifier> blockMembership;
//    private static int id = 0;
//    private final int idx = id++;

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
        Op04StructuredStatement.replaceInSources(this, replacement);
        Op04StructuredStatement.replaceInTargets(this, replacement);
        return replacement;
    }

    public void nopThis() {
        replaceStatementWithNOP("");
    }


    @Override
    public StructuredStatement getStatement() {
        return structuredStatement;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        structuredStatement.collectTypeUsages(collector);
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
        structuredStatement = newTarget;
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
        return blockMembership;
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
        return structuredStatement.toString();
//        return structuredStatement.getClass().getSimpleName().toString();
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
//            logger.warning("From " + wasIn + " to " + nowIn + " = " + nowCopy);
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

    private static class AnonymousBlockExtractor implements StructuredStatementTransformer {
        @Override
        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
            if (in instanceof Block) {
                Block block = (Block) in;
                block.extractAnonymousBlocks();
            }
            in.transformStructuredChildren(this, scope);
            return in;
        }
    }

    private static class EmptyCatchTidier implements StructuredStatementTransformer {
        @Override
        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
            if (in instanceof UnstructuredCatch) {
                return ((UnstructuredCatch) in).getCatchForEmpty();
            }
            in.transformStructuredChildren(this, scope);
            return in;
        }
    }

    private static class TryCatchTidier implements StructuredStatementTransformer {
        @Override
        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
            if (in instanceof Block) {
                // Search for try statements, see if we can combine following catch statements with them.
                Block block = (Block) in;
                block.combineTryCatch();
            }
            in.transformStructuredChildren(this, scope);
            return in;
        }
    }

    private static class Inliner implements StructuredStatementTransformer {
        @Override
        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
            in.transformStructuredChildren(this, scope);
            if (in instanceof Block) {
                Block block = (Block) in;
                block.combineInlineable();
            }
            return in;
        }
    }

    public static StructuredStatement transformWithScope(StructuredScope scope, StructuredStatement stm) {
        Set<Op04StructuredStatement> nextFallThrough = scope.getNextFallThrough(stm);
        Op04StructuredStatement target = stm.getContainer().getTargets().get(0);
        if (nextFallThrough.contains(target)) {
            // Ok, fell through.  If we're the last statement of the current scope,
            // and the current scope has fallthrough, we can be removed.  Otherwise we
            // need to be translated to a break.
            if (scope.statementIsLast(stm)) {
                return new StructuredComment("");
            } else {
                return stm;
            }
        }
        return stm;
    }

    private static class StructuredGotoRemover implements StructuredStatementTransformer {
        @Override
        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
            in.transformStructuredChildren(this, scope);
            if (in instanceof UnstructuredGoto ||
                    in instanceof UnstructuredAnonymousBreak) {
                in = transformWithScope(scope, in);
            }
            return in;
        }
    }

    private static class PointlessBlockRemover implements StructuredStatementTransformer {
        @Override
        public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
            in.transformStructuredChildren(this, scope);
            if (in instanceof CanRemovePointlessBlock) {
                ((CanRemovePointlessBlock) in).removePointlessBlocks(scope);
            }
            return in;
        }
    }

    public void transform(StructuredStatementTransformer transformer, StructuredScope scope) {
        StructuredStatement old = structuredStatement;
        structuredStatement = transformer.transform(structuredStatement, scope);
        if (structuredStatement != old && structuredStatement != null) {
            structuredStatement.setContainer(this);
        }
    }

    /*
     * If we have any UnstructuredAnonBreakTargets in a block, starting at the last one, pull them into sub-blocks.
     */
    public static void insertAnonymousBlocks(Op04StructuredStatement root) {
        root.transform(new AnonymousBlockExtractor(), new StructuredScope());
    }

    /*
     * mutually exclusive blocks may have trailling gotos after them.  It's hard to remove them prior to here, but now we have
     * structure, we can find them more easily.
     */
    public static void tidyEmptyCatch(Op04StructuredStatement root) {
        root.transform(new EmptyCatchTidier(), new StructuredScope());
    }

    public static void tidyTryCatch(Op04StructuredStatement root) {
        root.transform(new TryCatchTidier(), new StructuredScope());
    }

    public static void inlinePossibles(Op04StructuredStatement root) {
        root.transform(new Inliner(), new StructuredScope());
    }

    public static void tidyVariableNames(Method method, Op04StructuredStatement root) {
        new VariableNameTidier(method).transform(root);
    }

    public static void removePointlessReturn(Op04StructuredStatement root) {
        StructuredStatement statement = root.getStatement();
        if (statement instanceof Block) {
            Block block = (Block) statement;
            block.removeLastNVReturn();
        }
    }


    public static void tidyTypedBooleans(Op04StructuredStatement root) {
        new TypedBooleanTidier().transform(root);
    }

    public static void prettifyBadLoops(Op04StructuredStatement root) {
        new BadLoopPrettifier().transform(root);
    }

    public static void removeStructuredGotos(Op04StructuredStatement root) {
        root.transform(new StructuredGotoRemover(), new StructuredScope());
    }

    public static void removePointlessBlocks(Op04StructuredStatement root) {
        root.transform(new PointlessBlockRemover(), new StructuredScope());
    }

    /*
     * We've got structured (hopefully) code now, so we can find the initial unbranched assignment points
     * for any given variable.
     *
     * We can also discover if stack locations have been re-used with a type change - this would have resulted
     * in what looks like invalid variable re-use, which we can now convert.
     */
    public static void discoverVariableScopes(Method method, Op04StructuredStatement root, VariableFactory variableFactory) {
        LValueScopeDiscoverer scopeDiscoverer = new LValueScopeDiscoverer(method.getMethodPrototype(), variableFactory);
        root.traceLocalVariableScope(scopeDiscoverer);
        // We should have found scopes, now update to reflect this.
        scopeDiscoverer.markDiscoveredCreations();
    }

    private static LValue removeSyntheticConstructorParams(Method method, Op04StructuredStatement root, boolean isInstance) {
        MethodPrototype prototype = method.getMethodPrototype();
        // method.getConstructorFlag());
        List<LocalVariable> vars = prototype.getComputedParameters();
        if (vars.isEmpty()) return null;
        FieldVariable matchedLValue = null;

        Map<LValue, LValue> replacements = MapFactory.newMap();

        List<ConstructorInvokationAnoynmousInner> usages = method.getClassFile().getAnonymousUsages();
        /*
         * In normal usage, there will be only one instance of the construction of an anonymous inner.
         * If there are multiple, then we will have an issue rewriting the inner variables to match the outer
         * ones.
         */
        ConstructorInvokationAnoynmousInner usage = usages.size() == 1 ? usages.get(0) : null;

        if (isInstance) {
            LocalVariable outerThis = vars.get(0);
            // Todo : Should we test that it's the right type?  Already been done, really....

            InnerClassConstructorRewriter innerClassConstructorRewriter = new InnerClassConstructorRewriter(method.getClassFile(), outerThis);
            innerClassConstructorRewriter.rewrite(root);
            matchedLValue = innerClassConstructorRewriter.getMatchedField();
            if (matchedLValue != null) {
                /* If there was a value to match, we now have to replace the parameter with the member anywhere it was used
                 * in the constructor.
                 */
                ClassFileField classFileField = matchedLValue.getClassFileField();
                classFileField.markHidden();
                classFileField.markSyntheticOuterRef();

                replacements.put(outerThis, matchedLValue);
                innerClassConstructorRewriter.getAssignmentStatement().getContainer().nopOut();
                prototype.hide(0);
            }
        }

        /* If this inner class is an anonymous inner class, it could capture outer locals directly.
         * for all the other members - we'll search for any private final members which are initialised in the constructor
         * and alias those members to the argument that called them.
         */
        if (usage != null) {
            List<Expression> actualArgs = usage.getArgs();
            if (actualArgs.size() != vars.size()) {
                throw new IllegalStateException();
            }
            int start = isInstance ? 1 : 0;
            for (int x = start, len = vars.size(); x < len; ++x) {
                LocalVariable protoVar = vars.get(x);
                Expression arg = actualArgs.get(x);

                arg = CastExpression.removeImplicit(arg);
                /*
                 * For this to be a captured variable, it needs to not be computed - i.e. an Lvalue.
                 */
                if (!(arg instanceof LValueExpression)) continue;
                LValue lValueArg = ((LValueExpression) arg).getLValue();
                String name = null;
                if (!(lValueArg instanceof LocalVariable)) continue;
                LocalVariable localVariable = (LocalVariable) lValueArg;

                InnerClassConstructorRewriter innerClassConstructorRewriter = new InnerClassConstructorRewriter(method.getClassFile(), protoVar);
                innerClassConstructorRewriter.rewrite(root);
                FieldVariable matchedField = innerClassConstructorRewriter.getMatchedField();
                if (matchedField != null) {
                    // Nop out the assign statement, rename the field, hide the argument.
                    innerClassConstructorRewriter.getAssignmentStatement().getContainer().nopOut();
                    // We need to link the name to the outer variable in such a way that if that changes name,
                    // we don't lose it.
                    //
                    // Once this has occurred, there's a possibility that we may have caused collisions
                    // between these renamed members and locals in other code.
                    ClassFileField classFileField = matchedField.getClassFileField();
                    classFileField.overrideName(localVariable.getName().getStringName());
                    classFileField.markSyntheticOuterRef();
                    classFileField.markHidden();
                    prototype.hide(x);
                }
            }
        }


        if (!replacements.isEmpty()) {
            LValueReplacingRewriter lValueReplacingRewriter = new LValueReplacingRewriter(replacements);
            MiscStatementTools.applyExpressionRewriter(root, lValueReplacingRewriter);
        }

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
            if (classFile.isInnerClass()) {
                res = removeSyntheticConstructorParams(method, root, !classFile.testAccessFlag(AccessFlag.ACC_STATIC));
            }
        }
        return res;
    }

    public static void inlineSyntheticAccessors(DCCommonState state, Method method, Op04StructuredStatement root) {
        JavaTypeInstance classType = method.getClassFile().getClassType();
        new SyntheticAccessorRewriter(state, classType).rewrite(root);
    }

    public static void removeConstructorBoilerplate(Op04StructuredStatement root) {
        new RedundantSuperRewriter().rewrite(root);
    }

    public static void rewriteLambdas(DCCommonState state, Method method, Op04StructuredStatement root) {
        Options options = state.getOptions();
        if (!options.getOption(OptionsImpl.REWRITE_LAMBDAS, method.getClassFile().getClassFileVersion())) return;

        new LambdaRewriter(state, method.getClassFile()).rewrite(root);
    }

    public static void removeUnnecessaryVarargArrays(Options options, Method method, Op04StructuredStatement root) {
        new VarArgsRewriter().rewrite(root);
    }

    public static void removePrimitiveDeconversion(Options options, Method method, Op04StructuredStatement root) {
        if (!options.getOption(OptionsImpl.SUGAR_BOXING)) return;

        root.transform(new PrimitiveBoxingRewriter(), new StructuredScope());
    }

    public static void replaceNestedSyntheticOuterRefs(Op04StructuredStatement root) {
        List<StructuredStatement> statements = MiscStatementTools.linearise(root);
        //
        // It strikes me I could do this as a map replace, if I generate the set of possible rewrites.
        // probably a bit gross though ;)
        //
        if (statements == null) return;

        SyntheticOuterRefRewriter syntheticOuterRefRewriter = new SyntheticOuterRefRewriter();
        for (StructuredStatement statement : statements) {
            statement.rewriteExpressions(syntheticOuterRefRewriter);
        }

    }

    /*
     * there /should/ never be any loose catch statements.
     */
    public static void applyChecker(Op04Checker checker, Op04StructuredStatement root, DecompilerComments comments) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(checker, structuredScope);
        checker.commentInto(comments);

    }
}
