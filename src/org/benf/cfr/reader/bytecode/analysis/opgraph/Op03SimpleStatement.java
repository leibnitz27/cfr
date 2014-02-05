package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.ExpressionReplacingRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.NOPSearchingExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.*;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.ArrayVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AccountingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.finalhelp.*;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.stack.StackEntry;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitch;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitchEntry;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 19/03/2012
 * Time: 06:52
 * To change this template use File | Settings | File Templates.
 */
public class Op03SimpleStatement implements MutableGraph<Op03SimpleStatement>, Dumpable, StatementContainer<Statement>, IndexedStatement {
    private static final Logger logger = LoggerFactory.create(Op03SimpleStatement.class);

    private final List<Op03SimpleStatement> sources = ListFactory.newList();
    private final List<Op03SimpleStatement> targets = ListFactory.newList();
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
    //
    // blocks ended just before this.  (used to resolve break statements).
    //
    private final Set<BlockIdentifier> immediatelyAfterBlocks = SetFactory.newSet();

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

    @Override
    public void nopOut() {
        if (this.isNop) {
            return;
            // throw new ConfusedCFRException("Trying to nopOut a node which was already nopped.");
        }
        if (this.targets.isEmpty()) {
            this.containedStatement = new Nop();
            this.isNop = true;
            containedStatement.setContainer(this);
            return;
        }

        if (this.targets.size() != 1) {
            throw new ConfusedCFRException("Trying to nopOut a node with multiple targets");
        }
        this.containedStatement = new Nop();
        this.isNop = true;
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
        // And, take all the blocks which were ending here, and add them to the blocksEnding in target
        target.immediatelyAfterBlocks.addAll(immediatelyAfterBlocks);
        immediatelyAfterBlocks.clear();
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
        this.isNop = true;
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
        return immediatelyAfterBlocks;
    }

    @Override
    public void copyBlockInformationFrom(StatementContainer other) {
        Op03SimpleStatement other3 = (Op03SimpleStatement) other;
        this.immediatelyAfterBlocks.addAll(other.getBlocksEnded());
        this.containedInBlocks.addAll(other.getBlockIdentifiers());
        //
        // This is annoying, we only have space for one first in block.  TBH, this is a weak bit of
        // metadata, we should lose it.
        if (this.firstStatementInThisBlock == null) this.firstStatementInThisBlock = other3.firstStatementInThisBlock;
    }

    private boolean isNop() {
        return isNop;
    }

    public void replaceBlockIfIn(BlockIdentifier oldB, BlockIdentifier newB) {
        if (containedInBlocks.remove(oldB)) {
            containedInBlocks.add(newB);
        }
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

    private LValue getCreatedLValue() {
        return containedStatement.getCreatedLValue();
    }

    @Override
    public InstrIndex getIndex() {
        return index;
    }

    public void setIndex(InstrIndex index) {
        this.index = index;
    }

    /*
     * TODO : This is gross.
     */
    private void markBlockStatement(BlockIdentifier blockIdentifier, Op03SimpleStatement lastInBlock, Op03SimpleStatement blockEnd, List<Op03SimpleStatement> statements) {
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
        if (this.firstStatementInThisBlock != null && this.firstStatementInThisBlock != blockIdentifier) {
            throw new ConfusedCFRException("Statement already marked as first in another block");
        }
        this.firstStatementInThisBlock = blockIdentifier;
    }

    private void markPostBlock(BlockIdentifier blockIdentifier) {
        this.immediatelyAfterBlocks.add(blockIdentifier);
    }

    private void markBlock(BlockIdentifier blockIdentifier) {
        containedInBlocks.add(blockIdentifier);
    }

    private void collect(LValueAssignmentAndAliasCondenser lValueAssigmentCollector) {
        containedStatement.collectLValueAssignments(lValueAssigmentCollector);
    }

    private void condense(LValueRewriter lValueRewriter) {
        containedStatement.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers);
    }

    private void rewrite(ExpressionRewriter expressionRewriter) {
        containedStatement.rewriteExpressions(expressionRewriter, ssaIdentifiers);
    }

    private void findCreation(CreationCollector creationCollector) {
        containedStatement.collectObjectCreation(creationCollector);
    }

    public boolean condenseWithNextConditional() {
        return containedStatement.condenseWithNextConditional();
    }

    private void simplifyConditional() {
        if (containedStatement instanceof IfStatement) {
            IfStatement ifStatement = (IfStatement) containedStatement;
            ifStatement.simplifyCondition();
        }
    }

    public class GraphVisitorCallee implements BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>> {
        private final List<Op03SimpleStatement> reachableNodes;

        public GraphVisitorCallee(List<Op03SimpleStatement> reachableNodes) {
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

    /*
     * Op02 and 3 should both implement indexable, so we can share this.
     */
    public static class CompareByIndex implements Comparator<Op03SimpleStatement> {
        @Override
        public int compare(Op03SimpleStatement a, Op03SimpleStatement b) {
            int res = a.getIndex().compareTo(b.getIndex());
            if (res == 0) {
                throw new ConfusedCFRException("Can't sort instructions:\n" + a + "\n" + b);
            }
            return res;
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
        if (needsLabel()) dumper.print(getLabel() + ":\n");
        for (BlockIdentifier blockIdentifier : containedInBlocks) {
            dumper.print(blockIdentifier + " ");
        }
//        dumper.print("{" + ssaIdentifiers + "}");
        getStatement().dump(dumper);
    }

    public static void dumpAll(List<Op03SimpleStatement> statements, Dumper dumper) {
        for (Op03SimpleStatement statement : statements) {
            statement.dumpInner(dumper);
        }
    }

    @Override
    public Dumper dump(Dumper dumper) {
        dumper.print("**********\n");
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
        dumper.print("**********\n");
        return dumper;
    }

    public Op04StructuredStatement getStructuredStatementPlaceHolder() {
        return new Op04StructuredStatement(
                index,
                containedInBlocks,
                containedStatement.getStructuredStatement());
    }

    private boolean isCompound() {
        return containedStatement.isCompound();
    }

    private List<Op03SimpleStatement> splitCompound() {
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
        this.isNop = true;
        return result;
    }

    public static void flattenCompoundStatements(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> newStatements = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            if (statement.isCompound()) {
                newStatements.addAll(statement.splitCompound());
            }
        }
        statements.addAll(newStatements);
    }

    private void collectLocallyMutatedVariables(SSAIdentifierFactory<LValue> ssaIdentifierFactory) {
        this.ssaIdentifiers = containedStatement.collectLocallyMutatedVariables(ssaIdentifierFactory);
    }

    public static void assignSSAIdentifiers(Method method, List<Op03SimpleStatement> statements) {

        SSAIdentifierFactory<LValue> ssaIdentifierFactory = new SSAIdentifierFactory<LValue>();

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

    /*
     * if we have a chain (DIRECTLY CONNECTED) of
     *
     * b = c = d;
     *
     * we'll end up with
     *
     * b = d; c = d   OR   c = d; b = d
     * Then we need to massage them into an assignment chain.
     *
     * Find them by following chains where the RHS is the same.
     */
    public static void condenseLValueChain1(List<Op03SimpleStatement> statements) {

        for (Op03SimpleStatement statement : statements) {
            Statement stm = statement.getStatement();
            if (stm instanceof AssignmentSimple) {
                if (statement.getTargets().size() == 1) {
                    Op03SimpleStatement statement2 = statement.getTargets().get(0);
                    if (statement2.getSources().size() != 1) {
                        continue;
                    }
                    Statement stm2 = statement2.getStatement();
                    if (stm2 instanceof AssignmentSimple) {
                        applyLValueSwap((AssignmentSimple) stm, (AssignmentSimple) stm2, statement, statement2);
                    }
                }
            }
        }
    }

    public static void applyLValueSwap(AssignmentSimple a1, AssignmentSimple a2,
                                       Op03SimpleStatement stm1, Op03SimpleStatement stm2) {
        Expression r1 = a1.getRValue();
        Expression r2 = a2.getRValue();
        if (!r1.equals(r2)) return;
        LValue l1 = a1.getCreatedLValue();
        LValue l2 = a2.getCreatedLValue();

        if ((l1 instanceof StackSSALabel) && !(l2 instanceof StackSSALabel)) {
            stm1.replaceStatement(a2);
            stm2.replaceStatement(new AssignmentSimple(l1, new LValueExpression(l2)));
        }
    }

    public static void condenseLValueChain2(List<Op03SimpleStatement> statements) {

        for (Op03SimpleStatement statement : statements) {
            Statement stm = statement.getStatement();
            if (stm instanceof AssignmentSimple) {
                if (statement.getTargets().size() == 1) {
                    Op03SimpleStatement statement2 = statement.getTargets().get(0);
                    if (statement2.getSources().size() != 1) {
                        continue;
                    }
                    Statement stm2 = statement2.getStatement();
                    if (stm2 instanceof AssignmentSimple) {
                        applyLValueCondense((AssignmentSimple) stm, (AssignmentSimple) stm2, statement, statement2);
                    }
                }
            }
        }
    }

    public static void applyLValueCondense(AssignmentSimple a1, AssignmentSimple a2,
                                           Op03SimpleStatement stm1, Op03SimpleStatement stm2) {
        Expression r1 = a1.getRValue();
        Expression r2 = a2.getRValue();
        LValue l1 = a1.getCreatedLValue();
        LValue l2 = a2.getCreatedLValue();
        if (!r2.equals(new LValueExpression(l1))) return;

        stm1.nopOut();

        Expression newRhs = null;
        if (r1 instanceof ArithmeticOperation && ((ArithmeticOperation) r1).isMutationOf(l1)) {
            ArithmeticOperation ar1 = (ArithmeticOperation) r1;
            AbstractMutatingAssignmentExpression me = ar1.getMutationOf(l1);
            newRhs = me;
        }
        if (newRhs == null) newRhs = new AssignmentExpression(l1, r1, true);
        stm2.replaceStatement(new AssignmentSimple(l2, newRhs));
    }

    public static void determineFinal(List<Op03SimpleStatement> statements, VariableFactory variableFactory) {
    }

    public static void condenseLValues(List<Op03SimpleStatement> statements) {

        /*
         * [todo - fix accounting].
         * Unfortunately, the accounting for stack entries is a bit wrong.  This pass will make
         * sure it's correct. :P
         */
        AccountingRewriter accountingRewriter = new AccountingRewriter();
        for (Op03SimpleStatement statement : statements) {
            statement.rewrite(accountingRewriter);
        }
        accountingRewriter.flush();


        LValueAssignmentAndAliasCondenser lValueAssigmentCollector = new LValueAssignmentAndAliasCondenser();
        for (Op03SimpleStatement statement : statements) {
            statement.collect(lValueAssigmentCollector);
        }

        /*
         * Can we replace any mutable values?
         * If we found any on the first pass, we will try to move them here.
         */
        LValueAssignmentAndAliasCondenser.MutationRewriterFirstPass firstPassRewriter = lValueAssigmentCollector.getMutationRewriterFirstPass();
        if (firstPassRewriter != null) {
            for (Op03SimpleStatement statement : statements) {
                statement.condense(firstPassRewriter);
            }

            LValueAssignmentAndAliasCondenser.MutationRewriterSecondPass secondPassRewriter = firstPassRewriter.getSecondPassRewriter();
            if (secondPassRewriter != null) {
                for (Op03SimpleStatement statement : statements) {
                    statement.condense(secondPassRewriter);
                }
            }
        }

        /*
         * Don't actually rewrite anything, but have an additional pass through to see if there are any aliases we can replace.
         */
        LValueAssignmentAndAliasCondenser.AliasRewriter multiRewriter = lValueAssigmentCollector.getAliasRewriter();
        for (Op03SimpleStatement statement : statements) {
            statement.condense(multiRewriter);
        }
        multiRewriter.inferAliases();

        for (Op03SimpleStatement statement : statements) {
            statement.condense(lValueAssigmentCollector);
        }
    }

    /* We're searching for something a bit too fiddly to use wildcards on,
     * so lots of test casting :(
     */
    private static void replacePreChangeAssignment(Op03SimpleStatement statement) {
        AssignmentSimple assignmentSimple = (AssignmentSimple) statement.containedStatement;

        LValue lValue = assignmentSimple.getCreatedLValue();

        // Is it an arithop
        Expression rValue = assignmentSimple.getRValue();
        if (!(rValue instanceof ArithmeticOperation)) return;

        // Which is a mutation
        ArithmeticOperation arithmeticOperation = (ArithmeticOperation) rValue;
        if (!arithmeticOperation.isMutationOf(lValue)) return;

        // Create an assignment prechange with the mutation
        AbstractMutatingAssignmentExpression mutationOperation = arithmeticOperation.getMutationOf(lValue);

        AssignmentPreMutation res = new AssignmentPreMutation(lValue, mutationOperation);
        statement.replaceStatement(res);
    }

    public static void replacePreChangeAssignments(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> assignments = Functional.filter(statements, new TypeFilter<AssignmentSimple>(AssignmentSimple.class));
        for (Op03SimpleStatement assignment : assignments) {
            replacePreChangeAssignment(assignment);
        }
    }

    private static void eliminateCatchTemporary(Op03SimpleStatement catchh) {
        if (catchh.targets.size() != 1) return;
        Op03SimpleStatement maybeAssign = catchh.targets.get(0);

        CatchStatement catchStatement = (CatchStatement) catchh.getStatement();
        LValue catching = catchStatement.getCreatedLValue();

        if (!(catching instanceof StackSSALabel)) return;
        StackSSALabel catchingSSA = (StackSSALabel) catching;
        if (catchingSSA.getStackEntry().getUsageCount() != 1) return;

        while (maybeAssign.getStatement() instanceof TryStatement) {
            // Note that the 'tried' path is always path 0 of a try statement.
            maybeAssign = maybeAssign.targets.get(0);
        }
        WildcardMatch match = new WildcardMatch();
        if (!match.match(new AssignmentSimple(match.getLValueWildCard("caught"), new StackValue(catchingSSA)),
                maybeAssign.getStatement())) {
            return;
        }

        // Hurrah - maybeAssign is an assignment of the caught value.
        catchh.replaceStatement(new CatchStatement(catchStatement.getExceptions(), match.getLValueWildCard("caught").getMatch()));
        maybeAssign.nopOut();
    }

    public static void eliminateCatchTemporaries(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> catches = Functional.filter(statements, new TypeFilter<CatchStatement>(CatchStatement.class));
        for (Op03SimpleStatement catchh : catches) {
            eliminateCatchTemporary(catchh);
        }
    }

    // Expression statements which can't have any effect can be removed.
    public static void removePointlessExpressionStatements(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> exrps = Functional.filter(statements, new TypeFilter<ExpressionStatement>(ExpressionStatement.class));
        for (Op03SimpleStatement esc : exrps) {
            ExpressionStatement es = (ExpressionStatement) esc.getStatement();
            Expression expression = es.getExpression();
            if (expression instanceof LValueExpression ||
                    expression instanceof StackValue ||
                    expression instanceof Literal) {
                esc.nopOut();
            }
        }
        List<Op03SimpleStatement> sas = Functional.filter(statements, new TypeFilter<AssignmentSimple>(AssignmentSimple.class));
        for (Op03SimpleStatement ass : sas) {
            AssignmentSimple assignmentSimple = (AssignmentSimple) ass.containedStatement;
            LValue lValue = assignmentSimple.getCreatedLValue();
            Expression rValue = assignmentSimple.getRValue();
            if (rValue.getClass() == LValueExpression.class) {
                LValueExpression lValueExpression = (LValueExpression) rValue;
                if (lValueExpression.getLValue().equals(lValue)) {
                    ass.nopOut();
                }
            }
        }

    }

    private static class UsageWatcher implements LValueRewriter<Statement> {
        private final LValue needle;
        boolean found = false;

        private UsageWatcher(LValue needle) {
            this.needle = needle;
        }

        @Override
        public Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer<Statement> statementContainer) {

            return null;
        }

        @Override
        public boolean explicitlyReplaceThisLValue(LValue lValue) {
            return true;
        }

        public boolean isFound() {
            return found;
        }
    }

    /*
    * preChange is --x / ++x.
    *
    * Can we find an immediate guaranteed TEMPORARY dominator which takes the previous value of x?
    *
    * ie
    *
    * v0 = x
    * ++x
    *
    * -->
    *
    * v0 = x++
    */
    private static void pushPreChangeBack(Op03SimpleStatement preChange) {
        AssignmentPreMutation mutation = (AssignmentPreMutation) preChange.containedStatement;
        Op03SimpleStatement current = preChange;

        LValue mutatedLValue = mutation.getCreatedLValue();
        Expression lvalueExpression = new LValueExpression(mutatedLValue);
        UsageWatcher usageWatcher = new UsageWatcher(mutatedLValue);

        while (true) {
            List<Op03SimpleStatement> sources = current.getSources();
            if (sources.size() != 1) return;

            current = sources.get(0);
            /*
             * If current makes use of x in any way OTHER than a simple assignment, we have to abort.
             * Otherwise, if it's v0 = x, it's a candidate.
             */
            Statement innerStatement = current.getStatement();
            if (innerStatement instanceof AssignmentSimple) {
                AssignmentSimple assignmentSimple = (AssignmentSimple) innerStatement;
                if (assignmentSimple.getRValue().equals(lvalueExpression)) {
                    LValue tgt = assignmentSimple.getCreatedLValue();
                    preChange.nopOut();
                    current.replaceStatement(new AssignmentSimple(tgt, mutation.getPostMutation()));
                    return;
                }
            }
            current.condense(usageWatcher);
            if (usageWatcher.isFound()) {
                /*
                 * Found a use "Before" assignment.
                 */
                return;
            }
        }
    }

    private static class StatementCanBePostMutation implements Predicate<Op03SimpleStatement> {
        @Override
        public boolean test(Op03SimpleStatement in) {
            AssignmentPreMutation assignmentPreMutation = (AssignmentPreMutation) in.getStatement();
            LValue lValue = assignmentPreMutation.getCreatedLValue();
            return (assignmentPreMutation.isSelfMutatingOp1(lValue, ArithOp.PLUS) ||
                    assignmentPreMutation.isSelfMutatingOp1(lValue, ArithOp.MINUS));
        }
    }

    public static void pushPreChangeBack(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> assignments = Functional.filter(statements, new TypeFilter<AssignmentPreMutation>(AssignmentPreMutation.class));
        assignments = Functional.filter(assignments, new StatementCanBePostMutation());

        for (Op03SimpleStatement assignment : assignments) {
            pushPreChangeBack(assignment);
        }
    }

    /*
     * Find all the constructors and initialisers.  If something is initialised and
     * constructed in one place each, we can guarantee that the construction happened
     * after the initialisation, so replace
     *
     * a1 = new foo
     * a1.<init>(x, y, z)
     *
     * with
     *
     * a1 = new foo(x,y,z)
     */
    public static void condenseConstruction(DCCommonState state, Method method, List<Op03SimpleStatement> statements) {
        CreationCollector creationCollector = new CreationCollector();
        for (Op03SimpleStatement statement : statements) {
            statement.findCreation(creationCollector);
        }
        creationCollector.condenseConstructions(method, state);
    }

    /*
     * lbl: [to which there is a backjump]
     * a = 3
     * if (a == 4) foo
     * ->
     * lbl:
     * if ((a=3)==4) foo
     */
    private static void rollAssignmentsIntoConditional(Op03SimpleStatement conditional) {
        /* Generate a list of all the assignments before this statement in a straight line, until there
         * is a back source.
         *
         * For each of these, IF that value is NOT used between its location and 'conditional', AND
         * the RHS is compatible with the SSAIdentifiers of 'conditional', then it can be inserted
         * as a mutating expression in 'conditional'.
         *
         */
    }

    public static void rollAssignmentsIntoConditionals(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> conditionals = Functional.filter(statements, new TypeFilter(IfStatement.class));
        for (Op03SimpleStatement conditional : conditionals) {
            rollAssignmentsIntoConditional(conditional);
        }
    }

    /*
     * We look for related groups of conditionals, such that
     *
     * if (c1) then b
     * if (c2) then a
     * b:
     *
     * === if (!c1 && c2) then a
     * b:
     */
    public static void condenseConditionals(List<Op03SimpleStatement> statements) {
        for (int x = 0; x < statements.size(); ++x) {
            boolean retry = false;
            do {
                retry = false;
                Op03SimpleStatement op03SimpleStatement = statements.get(x);
                // If successful, this statement will be nopped out, and the next one will be
                // the combination of the two.
                if (op03SimpleStatement.condenseWithNextConditional()) {
                    retry = true;
                    // If it worked, go back to the last nop, and retry.
                    // This could probably be refactored to do less work.....
                    do {
                        x--;
                    } while (statements.get(x).isNop() && x > 0);
                }
            } while (retry);
        }
    }

    public static void simplifyConditionals(List<Op03SimpleStatement> statements) {
        for (Op03SimpleStatement statement : statements) {
            statement.simplifyConditional();
        }
    }


    public static boolean condenseConditionals2(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> ifStatements = Functional.filter(statements, new TypeFilter<IfStatement>(IfStatement.class));
        boolean result = false;
        for (Op03SimpleStatement ifStatement : ifStatements) {
            if (condenseConditional2_type1(ifStatement, statements)) {
                result = true;
            }
        }
        return result;
    }

    private static void replaceReturningIf(Op03SimpleStatement ifStatement, boolean aggressive) {
        if (!(ifStatement.containedStatement.getClass() == IfStatement.class)) return;
        IfStatement innerIf = (IfStatement) ifStatement.containedStatement;
        if (ifStatement.getTargets().size() != 2) {
            int x = 1;
        }
        Op03SimpleStatement tgt = ifStatement.getTargets().get(1);
        final Op03SimpleStatement origtgt = tgt;
        boolean requireJustOneSource = !aggressive;
        do {
            Op03SimpleStatement next = followNopGoto(tgt, requireJustOneSource, aggressive);
            if (next == tgt) break;
            tgt = next;
        } while (true);
        Statement tgtStatement = tgt.containedStatement;
        if (tgtStatement instanceof ReturnStatement) {
            ifStatement.replaceStatement(new IfExitingStatement(innerIf.getCondition(), tgtStatement));
        } else {
            return;
        }
        origtgt.removeSource(ifStatement);
        ifStatement.removeTarget(origtgt);
    }

    private static void replaceReturningGoto(Op03SimpleStatement gotoStatement, boolean aggressive) {
        if (!(gotoStatement.containedStatement.getClass() == GotoStatement.class)) return;
        Op03SimpleStatement tgt = gotoStatement.getTargets().get(0);
        final Op03SimpleStatement origtgt = tgt;
        boolean requireJustOneSource = !aggressive;
        do {
            Op03SimpleStatement next = followNopGoto(tgt, requireJustOneSource, aggressive);
            if (next == tgt) break;
            tgt = next;
        } while (true);
        Statement tgtStatement = tgt.containedStatement;
        if (tgtStatement instanceof ReturnStatement) {
            gotoStatement.replaceStatement(tgtStatement);
        } else {
            return;
        }
        origtgt.removeSource(gotoStatement);
        gotoStatement.removeTarget(origtgt);
    }

    public static void replaceReturningIfs(List<Op03SimpleStatement> statements, boolean aggressive) {
        List<Op03SimpleStatement> ifStatements = Functional.filter(statements, new TypeFilter<IfStatement>(IfStatement.class));
        for (Op03SimpleStatement ifStatement : ifStatements) {
            replaceReturningIf(ifStatement, aggressive);
        }
    }

    /*
     * VERY aggressive options for simplifying control flow, at the cost of changing the appearance.
     *
     */
    public static void propagateToReturn(Method method, List<Op03SimpleStatement> statements) {
        boolean success = false;
        for (Op03SimpleStatement stm : statements) {
            Statement inner = stm.getStatement();
            if (inner.getClass() == AssignmentSimple.class) {
                /*
                 * This pass helps with scala and dex2jar style output - find a remaining assignment to a stack
                 * variable (or POSSIBLY a local), and follow it through.  If nothing intervenes, and we hit a return, we can
                 * simply replace the entry point.
                 *
                 * We agressively attempt to follow through computable literals.
                 */
                if (stm.getTargets().size() != 1)
                    continue; // shouldn't be possible to be other, but a pruning might have removed.
                AssignmentSimple assignmentSimple = (AssignmentSimple) inner;
                LValue lValue = assignmentSimple.getCreatedLValue();
                Expression rValue = assignmentSimple.getRValue();
                if (!(lValue instanceof StackSSALabel || lValue instanceof LocalVariable)) continue;
                Map<LValue, Literal> display = MapFactory.newMap();
                if (rValue instanceof Literal) {
                    display.put(lValue, (Literal) rValue);
                }
                success |= propagateLiteral(method, stm, stm.getTargets().get(0), lValue, rValue, display);
                // Note - we can't have another go with return back yet, as it would break ternary discovery.
            }
        }


        if (success) Op03SimpleStatement.replaceReturningIfs(statements, true);
    }

    public static void propagateToReturn2(Method method, List<Op03SimpleStatement> statements) {
        boolean success = false;
        for (Op03SimpleStatement stm : statements) {
            Statement inner = stm.getStatement();

            if (inner instanceof ReturnStatement) {
                /*
                 * Another very aggressive operation - find any goto which directly jumps to a return, and
                 * place a copy of the return in the goto.
                 *
                 * This will interfere with returning a ternary, however because it's an aggressive option, it
                 * won't be used unless needed.
                 *
                 * We look for returns rather than gotos, as returns are less common.
                 */
                success |= pushReturnBack(method, stm);
            }
        }
        if (success) Op03SimpleStatement.replaceReturningIfs(statements, true);
    }

    private static boolean pushReturnBack(Method method, Op03SimpleStatement stm) {

        ReturnStatement returnStatement = (ReturnStatement) stm.getStatement();

        List<Op03SimpleStatement> toRemove = null;
        for (Op03SimpleStatement src : stm.getSources()) {
            if (src.getStatement().getClass() == GotoStatement.class) {
                if (toRemove == null) toRemove = ListFactory.newList();
                toRemove.add(src);
            }
        }

        if (toRemove == null) return false;

        CloneHelper cloneHelper = new CloneHelper();
        for (Op03SimpleStatement remove : toRemove) {
            remove.replaceStatement(returnStatement.deepClone(cloneHelper));
            remove.removeTarget(stm);
            stm.removeSource(remove);
        }

        for (Op03SimpleStatement remove : toRemove) {
            pushReturnBack(method, remove);
        }

        return true;
    }

    /*
     * We require a straight through route, with no chances that any side effects have occured.
     * (i.e. call any methods, change members).
     * However, we can cope with further LITERAL assignments to locals and stackvars, and even
     * conditionals on them (if literal).
     *
     * This allows us to cope with the disgusting scala pattern.
     *
     * if (temp1) {
     *   ALSO JUMP HERE FROM ELSEWHERE
     *   temp2 = true;
     * } else {
     *   temp2 = false;
     * }
     * if (temp2) {
     *   temp3 = true;
     * } else {
     *   temp3 = false;
     * }
     * return temp3;
     *
     */
    private static boolean propagateLiteral(Method method, Op03SimpleStatement original, final Op03SimpleStatement orignext, final LValue originalLValue, final Expression originalRValue, Map<LValue, Literal> display) {
        Op03SimpleStatement current = orignext;
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        do {
            if (!seen.add(current)) return false;
            Class<?> cls = current.getStatement().getClass();
            List<Op03SimpleStatement> curTargets = current.getTargets();
            int nTargets = curTargets.size();
            if (cls == Nop.class) {
                if (nTargets != 1) return false;
                current = curTargets.get(0);
                continue;
            }
            if (cls == ReturnNothingStatement.class) break;
            if (cls == ReturnValueStatement.class) break;
            if (cls == GotoStatement.class ||
                    cls == MonitorExitStatement.class) {
                if (nTargets != 1) return false;
                current = curTargets.get(0);
                continue;
            }
            if (cls == AssignmentSimple.class) {
                AssignmentSimple assignmentSimple = (AssignmentSimple) current.getStatement();
                LValue lValue = assignmentSimple.getCreatedLValue();
                if (!(lValue instanceof StackSSALabel || lValue instanceof LocalVariable)) return false;
                Literal literal = assignmentSimple.getRValue().getComputedLiteral(display);
                if (literal == null) return false;
                display.put(lValue, literal);
                current = curTargets.get(0);
                continue;
            }

            // We /CAN/ actually cope with a conditional, if we're 100% sure of where we're going!
            if (cls == IfStatement.class) {
                IfStatement ifStatement = (IfStatement) current.getStatement();
                Literal literal = ifStatement.getCondition().getComputedLiteral(display);
                if (literal == null) return false;
                Boolean bool = literal.getValue().getMaybeBoolValue();
                if (bool == null) return false;
                if (bool) {
                    current = curTargets.get(1);
                } else {
                    current = curTargets.get(0);
                }
                continue;
            }
            return false;
        } while (true);

        Class<?> cls = current.getStatement().getClass();
        /*
         * If the original rValue is a literal, we can replace.  If not, we can't.
         */
        if (cls == ReturnNothingStatement.class) {
            if (!(originalRValue instanceof Literal)) return false;
            original.replaceStatement(new ReturnNothingStatement());
            orignext.removeSource(original);
            original.removeTarget(orignext);
            return true;
        }
        /*
         * Heuristic of doom.  If the ORIGINAL rvalue is a literal (i.e. side effect free), we can
         * ignore it, and replace the original assignment with the computed literal.
         *
         * If the original rvalue is NOT a literal, AND we are returning the original lValue, we can
         * return the original rValue.
         */
        if (cls == ReturnValueStatement.class) {
            ReturnValueStatement returnValueStatement = (ReturnValueStatement) current.getStatement();
            if (originalRValue instanceof Literal) {
                Expression e = returnValueStatement.getReturnValue().getComputedLiteral(display);
                if (e == null) return false;
                original.replaceStatement(new ReturnValueStatement(e, returnValueStatement.getFnReturnType()));
            } else {
                Expression ret = returnValueStatement.getReturnValue();
                if (!(ret instanceof LValueExpression)) return false;
                LValue retLValue = ((LValueExpression) ret).getLValue();
                if (!retLValue.equals(originalLValue)) return false;
                // NB : we don't have to clone rValue, as we're replacing the statement it came from.
                original.replaceStatement(new ReturnValueStatement(originalRValue, returnValueStatement.getFnReturnType()));
            }
            orignext.removeSource(original);
            original.removeTarget(orignext);
            return true;
        }
        return false;
    }

    // Should have a set to make sure we've not looped.
    private static Op03SimpleStatement followNopGoto(Op03SimpleStatement in, boolean requireJustOneSource, boolean aggressive) {
        if (in == null) {
            return null;
        }
        if (requireJustOneSource && in.sources.size() != 1) return in;
        if (in.targets.size() != 1) return in;
        Statement statement = in.getStatement();
        if (statement instanceof Nop ||
                statement instanceof GotoStatement ||
                (aggressive && statement instanceof CaseStatement) ||
                (aggressive && statement instanceof MonitorExitStatement)) {

            in = in.targets.get(0);
        }
        return in;
    }

    public static Op03SimpleStatement followNopGotoChain(Op03SimpleStatement in, boolean requireJustOneSource, boolean skipLabels) {
        if (in == null) return null;
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        do {
            if (!seen.add(in)) return in;
            Op03SimpleStatement next = followNopGoto(in, requireJustOneSource, skipLabels);
            if (next == in) return in;
            in = next;
        } while (true);
    }

    private static boolean condenseConditional2_type2(Op03SimpleStatement ifStatement) {
        return false;
    }

    /*
     * Look for a very specific pattern which is really awkward to pull out later
     *
     * if (w) goto a [taken1]                  [ifstatement1]
     * if (x) goto b [taken2]                  [ifstatement2]
     * [nottaken2] goto c [nottaken3]
     * [taken1] a: if (z) goto b  [taken3]     [ifstatement3]
     * [nottaken3] c:
     * ....
     * b:
     *
     *
     * if ((w && z) || x) goto b
     * goto c:
     *
     * c:
     */
    private static boolean condenseConditional2_type1(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> allStatements) {
        if (!(ifStatement.containedStatement instanceof IfStatement)) return false;

        final Op03SimpleStatement taken1 = ifStatement.getTargets().get(1);
        final Op03SimpleStatement nottaken1 = ifStatement.getTargets().get(0);
        if (!(nottaken1.containedStatement instanceof IfStatement)) return false;
        Op03SimpleStatement ifStatement2 = nottaken1;
        Op03SimpleStatement taken2 = ifStatement2.getTargets().get(1);
        Op03SimpleStatement nottaken2 = ifStatement2.getTargets().get(0);
        final Op03SimpleStatement nottaken2Immed = nottaken2;
        if (nottaken2Immed.sources.size() != 1) return false;
        Op03SimpleStatement notTaken2Source = ifStatement2;
        nottaken2 = followNopGotoChain(nottaken2, true, false);
        do {
            Op03SimpleStatement nontaken2rewrite = followNopGoto(nottaken2, true, false);
            if (nontaken2rewrite == nottaken2) break;
            notTaken2Source = nottaken2;
            nottaken2 = nontaken2rewrite;
        } while (true);
        if (!(taken1.containedStatement instanceof IfStatement)) return false;
        if (taken1.sources.size() != 1) return false;
        Op03SimpleStatement ifStatement3 = taken1;
        Op03SimpleStatement taken3 = ifStatement3.getTargets().get(1);
        Op03SimpleStatement nottaken3 = ifStatement3.getTargets().get(0);
        final Op03SimpleStatement nottaken3Immed = nottaken3;
        Op03SimpleStatement notTaken3Source = ifStatement3;
        do {
            Op03SimpleStatement nontaken3rewrite = followNopGoto(nottaken3, true, false);
            if (nontaken3rewrite == nottaken3) break;
            notTaken3Source = nottaken3;
            nottaken3 = nontaken3rewrite;
        } while (true);


        if (nottaken2 != nottaken3) return false;   // nottaken2 = nottaken3 = c
        if (taken2 != taken3) return false; // taken2 = taken3 = b;
        /*
         * rewrite as if ((w && z) || x)
         *
         *
         */
        IfStatement if1 = (IfStatement) ifStatement.containedStatement;
        IfStatement if2 = (IfStatement) ifStatement2.containedStatement;
        IfStatement if3 = (IfStatement) ifStatement3.containedStatement;

        ConditionalExpression newCond = new BooleanExpression(new TernaryExpression(
                if1.getCondition().getNegated().simplify(),
                if2.getCondition().getNegated().simplify(),
                if3.getCondition().getNegated().simplify())).getNegated();
//        TernaryExpression
//        ConditionalExpression newCond =
//                new BooleanOperation(
//                        new BooleanOperation(if1.getCondition(), if2.getCondition(), BoolOp.OR),
//                        if3.getCondition(), BoolOp.AND);

        ifStatement.replaceTarget(taken1, taken3);
        taken3.addSource(ifStatement);
        taken3.removeSource(ifStatement2);
        taken3.removeSource(ifStatement3);

        nottaken1.sources.remove(ifStatement);
        nottaken2Immed.replaceSource(ifStatement2, ifStatement);
        ifStatement.replaceTarget(nottaken1, nottaken2Immed);

//        nottaken3.removeSource(notTaken3Source);
        nottaken3.removeSource(notTaken3Source);

        ifStatement2.replaceStatement(new Nop());
        ifStatement3.replaceStatement(new Nop());
        ifStatement2.removeTarget(taken3);
        ifStatement3.removeTarget(taken3);

        ifStatement.replaceStatement(new IfStatement(newCond));

        /*
         * Now we're cleared up, see if nottaken2immed actually jumps straight to its target.
         */
        if (nottaken2Immed.sources.size() == 1) {
            if (nottaken2Immed.sources.get(0).getIndex().isBackJumpFrom(nottaken2Immed)) {
                if (nottaken2Immed.containedStatement.getClass() == GotoStatement.class) {
                    Op03SimpleStatement nottaken2ImmedTgt = nottaken2Immed.targets.get(0);
                    int idx = allStatements.indexOf(nottaken2Immed);
                    int idx2 = idx + 1;
                    do {
                        Op03SimpleStatement next = allStatements.get(idx2);
                        if (next.containedStatement instanceof Nop) {
                            idx2++;
                            continue;
                        }
                        if (next == nottaken2ImmedTgt) {
                            // Replace nottaken2 with nottaken2ImmedTgt.
                            nottaken2ImmedTgt.replaceSource(nottaken2Immed, ifStatement);
                            ifStatement.replaceTarget(nottaken2Immed, nottaken2ImmedTgt);
                        }
                        break;
                    } while (true);
                }
            }
        }

        return true;

    }

    /* If there is a chain of assignments before this conditional,
     * AND following single parents back, there is only conditionals and assignments,
     * AND this chain terminates in a back jump.....
     */
    private static boolean appropriateForIfAssignmentCollapse1(Op03SimpleStatement statement) {
        boolean extraCondSeen = false;
        boolean preCondAssignmentSeen = false;
        while (statement.sources.size() == 1) {
            Op03SimpleStatement source = statement.sources.get(0);
            // If there's a single parent, and it's a backjump, then I'm confused, as that means
            // we have a loop with no entry point...
            if (statement.getIndex().isBackJumpFrom(source)) break;
            Statement contained = source.containedStatement;
            if (contained instanceof AbstractAssignment) {
                preCondAssignmentSeen |= (!extraCondSeen);
            } else if (contained instanceof IfStatement) {
                extraCondSeen = true;
            } else {
                break;
            }
            statement = source;
        }
        if (!preCondAssignmentSeen) return false;
        // It turns out we generate better code with this, as we want (where possible) to /avoid/ pushing these
        // assignments.
        if (extraCondSeen) return false;
        /* If this statement has any backjumping sources then we consider it */
        InstrIndex statementIndex = statement.getIndex();
        for (Op03SimpleStatement source : statement.sources) {
            if (statementIndex.isBackJumpFrom(source)) return true;
        }
        return false;
    }

    private static boolean appropriateForIfAssignmentCollapse2(Op03SimpleStatement statement) {
        boolean extraCondSeen = false;
        boolean preCondAssignmentSeen = false;
        while (statement.sources.size() == 1) {
            Op03SimpleStatement source = statement.sources.get(0);
            if (source.getTargets().size() != 1) break;
            Statement contained = source.containedStatement;
            if (contained instanceof AbstractAssignment) {
                preCondAssignmentSeen = true;
            }
            statement = source;
        }
        if (!preCondAssignmentSeen) return false;
        return true;
    }

    // a=x
    // b=y
    // if (b==a)
    //
    // --> if ((b=x)==(a=y))
    private static void collapseAssignmentsIntoConditional(Op03SimpleStatement ifStatement, boolean testEclipse) {

        WildcardMatch wcm = new WildcardMatch();

        if (!(appropriateForIfAssignmentCollapse1(ifStatement) ||
                appropriateForIfAssignmentCollapse2(ifStatement))) return;
        IfStatement innerIf = (IfStatement) ifStatement.containedStatement;
        ConditionalExpression conditionalExpression = innerIf.getCondition();

        /*
         * The 'verify' block stops us winding up unless we'd do it into another conditional
         * or into a backjump.
         *
         * Otherwise, we end up with lots of code like
         *
         * int x
         * if ( (x=3) < y )
         *
         * rather than
         *
         * int x = 3
         * if (x < y)
         *
         * which is (a) ugly, and (b) screws with final analysis.
         */
        /*
         * HOWEVER - eclipse (of course) generates code which looks like
         *
         *
         */
        boolean eclipseHeuristic = testEclipse && ifStatement.getTargets().get(1).getIndex().isBackJumpFrom(ifStatement);
        if (!eclipseHeuristic) {
            Op03SimpleStatement statement = ifStatement;
            Set<Op03SimpleStatement> visited = SetFactory.newSet();
            verify:
            do {
                if (statement.sources.size() > 1) {
                    // Progress if we're a backjump target.
                    // Otherwise, we'll cause problems with assignments inside
                    // while conditionals.
                    InstrIndex statementIndex = statement.index;
                    for (Op03SimpleStatement source : statement.sources) {
                        if (statementIndex.isBackJumpFrom(source)) {
                            break verify;
                        }
                    }
                }
                if (statement.sources.isEmpty()) {
                    return;
                }
                statement = statement.sources.get(0);
                if (!visited.add(statement)) {
                    return;
                }
                Statement opStatement = statement.getStatement();
                if (opStatement instanceof IfStatement) break;
                if (opStatement instanceof Nop) continue;
                if (opStatement instanceof AbstractAssignment) continue;
                return;
            } while (true);
        }

        /* where possible, collapse any single parent assignments into this. */
        Op03SimpleStatement previousSource = null;
        while (ifStatement.sources.size() == 1) {
            Op03SimpleStatement source = ifStatement.sources.get(0);
            if (source == previousSource) return;
            previousSource = source;
            if (!(source.containedStatement instanceof AbstractAssignment)) return;
            LValue lValue = source.getCreatedLValue();
            // We don't have to worry about RHS having undesired side effects if we roll it into the
            // conditional - that has already happened.
            LValueUsageCollectorSimple lvc = new LValueUsageCollectorSimple();
            conditionalExpression.collectUsedLValues(lvc);
            if (!lvc.isUsed(lValue)) return;
            AbstractAssignment assignment = (AbstractAssignment) (source.containedStatement);

            // These two are horrible hacks to stop iterator loops being rewritten.
            // (Otherwise, if they're rolled up, the first statement inside the loop is the comparison instead
            // of the assignment.
//            Expression e1 = wcm.getMemberFunction("tst", "next", wcm.getExpressionWildCard("e"));
//            Expression e2 = new ArrayIndex(wcm.getExpressionWildCard("a"), new LValueExpression(wcm.getLValueWildCard("i")));
//            if (assignment instanceof AssignmentSimple) {
//                AssignmentSimple assignmentSimple = (AssignmentSimple) assignment;
//                if (assignmentSimple.isInitialAssign()) {
//                    Expression rV = assignment.getRValue();
//                    if (e1.equals(rV)) return;
//                    if (e2.equals(rV)) return;
//                }
//            }
            // This stops us rolling up finals, but at the cost of un-finalling them...
//            if (lValue instanceof LocalVariable) {
//                LocalVariable localVariable = (LocalVariable)lValue;
//                if (localVariable.isGuessedFinal()) return;
//            }

            AbstractAssignmentExpression assignmentExpression = assignment.getInliningExpression();
            LValueUsageCollectorSimple assignmentLVC = new LValueUsageCollectorSimple();
            assignmentExpression.collectUsedLValues(assignmentLVC);
            Set<LValue> used = SetFactory.newSet(assignmentLVC.getUsedLValues());
            used.remove(lValue);
            Set<LValue> usedComparison = SetFactory.newSet(lvc.getUsedLValues());

            // Avoid situation where we have
            // a = x
            // b = y.f(a)
            // if (a == b) <-- should not get rolled up.
            if (SetUtil.hasIntersection(used, usedComparison)) {
                return;
            }


            if (!ifStatement.getSSAIdentifiers().isValidReplacement(lValue, source.getSSAIdentifiers())) return;
            LValueAssignmentExpressionRewriter rewriter = new LValueAssignmentExpressionRewriter(lValue, assignmentExpression, source);
            Expression replacement = conditionalExpression.replaceSingleUsageLValues(rewriter, ifStatement.getSSAIdentifiers(), ifStatement);
            if (replacement == null) return;
            if (!(replacement instanceof ConditionalExpression)) return;
            innerIf.setCondition((ConditionalExpression) replacement);
        }

    }

    /*
     * Deal with
     *
     * a=b
     * if (a==4) {
     * }
     *
     * vs
     *
     * if ((a=b)==4) {
     * }
     *
     * We will always have the former, but (ONLY!) just after a backjump, (with only conditionals and assignments, and
     * single parents), we will want to run them together.
     */
    public static void collapseAssignmentsIntoConditionals(List<Op03SimpleStatement> statements, Options options) {
        // find all conditionals.
        List<Op03SimpleStatement> ifStatements = Functional.filter(statements, new TypeFilter<IfStatement>(IfStatement.class));
        boolean testEclipse = options.getOption(OptionsImpl.ECLIPSE);
        for (Op03SimpleStatement statement : ifStatements) {
            collapseAssignmentsIntoConditional(statement, testEclipse);
        }
    }

    public static List<Op03SimpleStatement> removeUnreachableCode(final List<Op03SimpleStatement> statements, final boolean checkBackJumps) {
        final Set<Op03SimpleStatement> reachable = SetFactory.newSet();
        reachable.add(statements.get(0));
        GraphVisitor<Op03SimpleStatement> gv = new GraphVisitorDFS<Op03SimpleStatement>(statements.get(0), new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
            @Override
            public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                reachable.add(arg1);
//                if (!statements.contains(arg1)) {
//                    throw new IllegalStateException("Statement missing");
//                }
                arg2.enqueue(arg1.getTargets());
                for (Op03SimpleStatement source : arg1.getSources()) {
//                    if (!statements.contains(source)) {
//                        throw new IllegalStateException("Source not in graph!");
//                    }
                    if (!source.getTargets().contains(arg1)) {
                        throw new IllegalStateException("Inconsistent graph " + source + " does not have a target of " + arg1);
                    }
                }
                for (Op03SimpleStatement test : arg1.getTargets()) {
                    // Also, check for backjump targets on non jumps.
                    Statement argContained = arg1.getStatement();
                    if (checkBackJumps) {
                        if (!(argContained instanceof JumpingStatement || argContained instanceof WhileStatement)) {
                            if (test.getIndex().isBackJumpFrom(arg1)) {
                                throw new IllegalStateException("Backjump on non jumping statement " + arg1);
                            }
                        }
                    }
                    if (!test.getSources().contains(arg1)) {
                        throw new IllegalStateException("Inconsistent graph " + test + " does not have a source " + arg1);
                    }
                }
            }
        });
        gv.process();

        List<Op03SimpleStatement> result = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            if (reachable.contains(statement)) {
                result.add(statement);
            }
        }
        // Too expensive....
        for (Op03SimpleStatement res1 : result) {
            List<Op03SimpleStatement> sources = ListFactory.newList(res1.getSources());
            for (Op03SimpleStatement source : sources) {
                if (!reachable.contains(source)) {
                    res1.removeSource(source);
                }
            }
        }
        return result;
    }

    /*
    * Filter out nops (where appropriate) and renumber.  For display purposes.
    */
    public static List<Op03SimpleStatement> renumber(List<Op03SimpleStatement> statements) {
        int newIndex = 0;
        boolean nonNopSeen = false;
        List<Op03SimpleStatement> result = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            if (!statement.isNop() || !nonNopSeen) {
                result.add(statement);
                if (!statement.isNop()) nonNopSeen = true;
            }
        }
        // Sort result by existing index.
        Collections.sort(result, new CompareByIndex());
        for (Op03SimpleStatement statement : result) {
            statement.setIndex(new InstrIndex(newIndex++));
        }
        return result;
    }

    public static void renumberInPlace(List<Op03SimpleStatement> statements) {
        int newIndex = 0;
        // Sort result by existing index.
        Collections.sort(statements, new CompareByIndex());
        for (Op03SimpleStatement statement : statements) {
            statement.setIndex(new InstrIndex(newIndex++));
        }
    }

    public static void reindexInPlace(List<Op03SimpleStatement> statements) {
        int newIndex = 0;
        for (Op03SimpleStatement statement : statements) {
            statement.setIndex(new InstrIndex(newIndex++));
        }
    }


    /* Remove pointless jumps 
    *
    * Normalise code by removing jumps which have been introduced to confuse.
    */
    public static void removePointlessJumps(List<Op03SimpleStatement> statements) {

        /*
         * Odd first pass, but we want to translate
         *
         * a : goto x
         * b : goto x
         *
         * into
         *
         * a : comment falls through to b
         * b : goto x
         */
        int size = statements.size() - 1;
        for (int x = 0; x < size - 1; ++x) {
            Op03SimpleStatement a = statements.get(x);
            Op03SimpleStatement b = statements.get(x + 1);
            if (a.containedStatement.getClass() == GotoStatement.class &&
                    b.containedStatement.getClass() == GotoStatement.class &&
                    a.targets.get(0) == b.targets.get(0)) {
                Op03SimpleStatement realTgt = a.targets.get(0);
                realTgt.removeSource(a);
                a.replaceTarget(realTgt, b);
                b.addSource(a);
                a.nopOut();
            }
        }


        // Do this pass first, as it needs spatial locality.
        for (int x = 0; x < size; ++x) {
            Op03SimpleStatement maybeJump = statements.get(x);
            if (maybeJump.containedStatement.getClass() == GotoStatement.class &&
                    maybeJump.targets.size() == 1 &&
                    maybeJump.targets.get(0) == statements.get(x + 1)) {
                maybeJump.nopOut();
            }
        }

        for (Op03SimpleStatement statement : statements) {
            Statement innerStatement = statement.getStatement();
            if (innerStatement instanceof JumpingStatement &&
                    statement.getSources().size() == 1 &&
                    statement.getTargets().size() == 1) {
                Op03SimpleStatement prior = statement.getSources().get(0);
                Statement innerPrior = prior.getStatement();
                if (innerPrior instanceof JumpingStatement) {
                    JumpingStatement jumpInnerPrior = (JumpingStatement) innerPrior;
                    Statement jumpingInnerPriorTarget = jumpInnerPrior.getJumpTarget();
                    if (jumpingInnerPriorTarget == innerStatement) {
                        statement.nopOut();
                    }
                }
            }
        }

        /*
         * Do this backwards.  Generally, there'll be more chains shortened that way.
         */
        for (int x = statements.size() - 1; x >= 0; --x) {
            Op03SimpleStatement statement = statements.get(x);
            Statement innerStatement = statement.getStatement();
            if (innerStatement.getClass() == GotoStatement.class) {
                GotoStatement innerGoto = (GotoStatement) innerStatement;
                switch (innerGoto.getJumpType()) {
                    case BREAK:
                        continue;
                }
                Op03SimpleStatement target = statement.targets.get(0);
                Op03SimpleStatement ultimateTarget = followNopGotoChain(target, false, false);
                if (target != ultimateTarget) {
                    ultimateTarget = maybeMoveTarget(ultimateTarget, statement, statements);
                    target.removeSource(statement);
                    statement.replaceTarget(target, ultimateTarget);
                    ultimateTarget.addSource(statement);
                }
            } else if (innerStatement.getClass() == IfStatement.class) {
                IfStatement ifStatement = (IfStatement) innerStatement;
                Op03SimpleStatement target = statement.targets.get(1);
                Op03SimpleStatement ultimateTarget = followNopGotoChain(target, false, false);
                if (target != ultimateTarget) {
                    ultimateTarget = maybeMoveTarget(ultimateTarget, statement, statements);
                    target.removeSource(statement);
                    statement.replaceTarget(target, ultimateTarget);
                    ultimateTarget.addSource(statement);
                }

            }

        }
    }

    /*
     * Convert:
     *
     * try {
     *   ...
     *   goto x
     * } catch (...) {
     *   // either block ending in goto x, or returning block.
     *   // essentially, either no forward exits, or last instruction is a forward exit to x.
     * }
     *
     * to
     * try {
     *   ...
     *   goto r; // (goto-out-of-try)
     * } catch (...) {
     *   ///
     * }
     * r:
     * goto x << REDIRECT
     *
     *
     */
    private static void extractExceptionJumps(Op03SimpleStatement tryi, List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tryTargets = tryi.getTargets();
        /*
         * Require that at least one block ends in a forward jump to the same block depth as tryi,
         * and that all others are either the same, or do not have a terminal forward jump.
         */
        Op03SimpleStatement uniqueForwardTarget = null;
        Set<BlockIdentifier> relevantBlocks = SetFactory.newSet();
        Op03SimpleStatement lastEnd = null;
        int lpidx = 0;
        for (Op03SimpleStatement tgt : tryTargets) {
            BlockIdentifier block = getBlockStart(((lpidx++ == 0) ? tryi : tgt).getStatement());
            if (block == null) return;
            relevantBlocks.add(block);
            Op03SimpleStatement lastStatement = getLastContiguousBlockStatement(block, in, tgt);
            if (lastStatement == null) return;
            if (lastStatement.getStatement().getClass() == GotoStatement.class) {
                Op03SimpleStatement lastTgt = lastStatement.getTargets().get(0);
                if (uniqueForwardTarget == null) {
                    uniqueForwardTarget = lastTgt;
                } else if (uniqueForwardTarget != lastTgt) return;
            }
            lastEnd = lastStatement;
        }
        if (uniqueForwardTarget == null) return;
        /*
         * We require that uniqueForwardTarget is in the same blocks as the original try
         * instruction.
         */
        if (!uniqueForwardTarget.getBlockIdentifiers().equals(tryi.getBlockIdentifiers())) return;

        /*
         * Find the instruction linearly after the final block.
         * If this is == uniqueForwardTarget, fine, mark those jumps as jumps out of try, and leave.
         * Otherwise, make sure this doesn't have any sources IN relevantBlocks, and place REDIRECT here.
         */
        int idx = in.indexOf(lastEnd);
        if (idx >= in.size() - 1) return;
        Op03SimpleStatement next = in.get(idx + 1);
        if (next == uniqueForwardTarget) {
            return;
            // handle.
        }
        for (Op03SimpleStatement source : next.getSources()) {
            if (SetUtil.hasIntersection(source.getBlockIdentifiers(), relevantBlocks)) {
                // Can't handle.
                return;
            }
        }
        List<Op03SimpleStatement> blockSources = ListFactory.newLinkedList();
        for (Op03SimpleStatement source : uniqueForwardTarget.getSources()) {
            if (SetUtil.hasIntersection(source.getBlockIdentifiers(), relevantBlocks)) {
                blockSources.add(source);
            }
        }
        Op03SimpleStatement indirect = new Op03SimpleStatement(next.getBlockIdentifiers(), new GotoStatement(), next.getIndex().justBefore());
        for (Op03SimpleStatement source : blockSources) {
            Statement srcStatement = source.getStatement();
            if (srcStatement instanceof GotoStatement) {
                ((GotoStatement) srcStatement).setJumpType(JumpType.GOTO_OUT_OF_TRY);
            }
            uniqueForwardTarget.removeSource(source);
            source.replaceTarget(uniqueForwardTarget, indirect);
            indirect.addSource(source);
        }
        indirect.addTarget(uniqueForwardTarget);
        uniqueForwardTarget.addSource(indirect);
        in.add(idx + 1, indirect);
    }

    private static BlockIdentifier getBlockStart(Statement statement) {
        Class<?> clazz = statement.getClass();
        if (clazz == TryStatement.class) {
            TryStatement tryStatement = (TryStatement) statement;
            return tryStatement.getBlockIdentifier();
        } else if (clazz == CatchStatement.class) {
            CatchStatement catchStatement = (CatchStatement) statement;
            return catchStatement.getCatchBlockIdent();
        } else if (clazz == FinallyStatement.class) {
            FinallyStatement finallyStatement = (FinallyStatement) statement;
            return finallyStatement.getFinallyBlockIdent();
        }
        return null;
    }

    public static void extractExceptionJumps(List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tries = Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement tryi : tries) {
            extractExceptionJumps(tryi, in);
        }
    }

    /*
     * If we're jumping into an instruction just after a try block, (or multiple try blocks), we move the jump to the try
     * block, IF we're jumping from outside the try block.
     */
    private static Op03SimpleStatement maybeMoveTarget(Op03SimpleStatement expectedRetarget, Op03SimpleStatement source, List<Op03SimpleStatement> statements) {
        if (expectedRetarget.getBlockIdentifiers().equals(source.getBlockIdentifiers())) return expectedRetarget;

        final int startIdx = statements.indexOf(expectedRetarget);
        int idx = startIdx;
        Op03SimpleStatement maybe = null;
        while (idx > 0 && statements.get(--idx).getStatement() instanceof TryStatement) {
            maybe = statements.get(idx);
            if (maybe.getBlockIdentifiers().equals(source.getBlockIdentifiers())) break;
        }
        if (maybe == null) return expectedRetarget;
        return maybe;
    }

    /*
     * Rewrite
     *
     * a:if (cond) goto x  [else z]
     * z : goto y:
     * x :
     *  blah
     * y:
     *
     * a->z,x
     * z->y
     *
     * as
     * a: if (!cond) goto y [else z]
     * z:nop
     * x:blah
     * y:
     *
     * a->z,y
     * z->x
     *
     * OR, better still
     *
     * a: if (!cond) goto y [else x]
     * [z REMOVED]
     * x: blah
     * y:
     *
     * We assume that statements are ordered.
     */
    public static void rewriteNegativeJumps(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> removeThese = ListFactory.newList();
        for (int x = 0; x < statements.size() - 2; ++x) {
            Op03SimpleStatement aStatement = statements.get(x);
            Statement innerAStatement = aStatement.getStatement();
            if (innerAStatement instanceof IfStatement) {
                Op03SimpleStatement zStatement = statements.get(x + 1);
                Op03SimpleStatement xStatement = statements.get(x + 2);

                if (aStatement.targets.get(0) == zStatement &&
                        aStatement.targets.get(1) == xStatement) {

                    Statement innerZStatement = zStatement.getStatement();
                    if (innerZStatement.getClass() == GotoStatement.class) {
                        // Yep, this is it.
                        Op03SimpleStatement yStatement = zStatement.targets.get(0);

                        // Order is important.
                        aStatement.replaceTarget(xStatement, yStatement);
                        aStatement.replaceTarget(zStatement, xStatement);

                        yStatement.replaceSource(zStatement, aStatement);
                        zStatement.sources.clear();
                        zStatement.targets.clear();
                        zStatement.containedStatement = new Nop();
                        removeThese.add(zStatement);

                        IfStatement innerAIfStatement = (IfStatement) innerAStatement;
                        innerAIfStatement.negateCondition();
                    }
                }
            }
        }
        statements.removeAll(removeThese);
    }

    /* DEAD CODE */

    private static boolean isDirectParentWithoutPassing(Op03SimpleStatement child, Op03SimpleStatement parent, Op03SimpleStatement barrier) {
        LinkedList<Op03SimpleStatement> tests = ListFactory.newLinkedList();
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        tests.add(child);
        seen.add(child);
        boolean hitParent = false;
        while (!tests.isEmpty()) {
            Op03SimpleStatement node = tests.removeFirst();
            if (node == barrier) continue;
            if (node == parent) {
                hitParent = true;
                continue;
            }
            List<Op03SimpleStatement> localParents = node.getSources();
            for (Op03SimpleStatement localParent : localParents) {
                if (seen.add(localParent)) {
                    tests.add(localParent);
                }
            }
        }
        return hitParent;
    }

    private static class IsBackJumpTo implements Predicate<Op03SimpleStatement> {
        private final InstrIndex thisIndex;

        private IsBackJumpTo(InstrIndex thisIndex) {
            this.thisIndex = thisIndex;
        }

        @Override
        public boolean test(Op03SimpleStatement in) {
            return thisIndex.isBackJumpFrom(in);
        }
    }

    private static class IsForwardJumpTo implements Predicate<Op03SimpleStatement> {
        private final InstrIndex thisIndex;

        private IsForwardJumpTo(InstrIndex thisIndex) {
            this.thisIndex = thisIndex;
        }

        @Override
        public boolean test(Op03SimpleStatement in) {
            return thisIndex.isBackJumpTo(in);
        }
    }

    // Todo - could get these out at the same time as below..... would add complexity though...
    private static Op03SimpleStatement getForInvariant(Op03SimpleStatement start, LValue invariant, BlockIdentifier whileLoop) {
        Op03SimpleStatement current = start;
        while (current.containedInBlocks.contains(whileLoop)) {
            if (current.containedStatement instanceof AbstractAssignment) {
                AbstractAssignment assignment = (AbstractAssignment) current.containedStatement;
                LValue assigned = assignment.getCreatedLValue();
                if (invariant.equals(assigned)) {
                    if (assignment.isSelfMutatingOperation()) return current;
                }
            }
            if (current.sources.size() > 1) break;
            Op03SimpleStatement next = current.sources.get(0);
            if (!current.index.isBackJumpTo(next)) break;
            current = next;
        }
        throw new ConfusedCFRException("Shouldn't be able to get here.");
    }

    private static Set<LValue> findForInvariants(Op03SimpleStatement start, BlockIdentifier whileLoop) {
        Set<LValue> res = SetFactory.newSet();
        Op03SimpleStatement current = start;
        while (current.containedInBlocks.contains(whileLoop)) {
            /* Note that here we're checking for assignments to determine what is suitable for lifting into a
             * for postcondition.
             *
             * This means that we will find x = x | fred ; x = x.doFred(); x = x + 1; etc.
             * As such, we want to make sure that we haven't transformed assignments into ExprStatements of
             * postAdjustExpressions yet.
             */
            if (current.containedStatement instanceof AbstractAssignment) {
                AbstractAssignment assignment = (AbstractAssignment) current.containedStatement;
                if (assignment.isSelfMutatingOperation()) {
                    res.add(assignment.getCreatedLValue());
                }
            }
            if (current.sources.size() > 1) break;
            Op03SimpleStatement next = current.sources.get(0);
            if (!current.index.isBackJumpTo(next)) break;
            current = next;
        }
        return res;
    }

    private static Op03SimpleStatement findSingleBackSource(Op03SimpleStatement start) {
        List<Op03SimpleStatement> startSources = Functional.filter(start.sources, new IsForwardJumpTo(start.index));
        if (startSources.size() != 1) {
            logger.info("** Too many back sources");
            return null;
        }
        return startSources.get(0);
    }

    private static Op03SimpleStatement findMovableAssignment(Op03SimpleStatement start, LValue lValue) {
        Op03SimpleStatement current = findSingleBackSource(start);
        if (current == null) {
            return null;
        }
        do {
            if (current.containedStatement instanceof AssignmentSimple) {
                AssignmentSimple assignmentSimple = (AssignmentSimple) current.containedStatement;
                if (assignmentSimple.getCreatedLValue().equals(lValue)) {
                    /* Verify that everything on the RHS is at the correct version */
                    Expression rhs = assignmentSimple.getRValue();
                    LValueUsageCollectorSimple lValueUsageCollector = new LValueUsageCollectorSimple();
                    rhs.collectUsedLValues(lValueUsageCollector);
                    if (SSAIdentifierUtils.isMovableUnder(lValueUsageCollector.getUsedLValues(), start.ssaIdentifiers, current.ssaIdentifiers)) {
                        return current;
                    } else {
                        logger.info("** incompatible sources");
                        return null;
                    }
                }
            }
            if (current.sources.size() != 1) {
                logger.info("** too many sources");
                return null;
            }
            current = current.sources.get(0);
        } while (current != null);
        return null;
    }

    private static void rewriteWhileAsFor(Op03SimpleStatement statement, List<Op03SimpleStatement> statements) {
        // Find the backwards jumps to this statement
        List<Op03SimpleStatement> backSources = Functional.filter(statement.sources, new IsBackJumpTo(statement.index));
        //
        // Determine what could be the loop invariant.
        //
        WhileStatement whileStatement = (WhileStatement) statement.containedStatement;
        ConditionalExpression condition = whileStatement.getCondition();
        Set<LValue> loopVariablePossibilities = condition.getLoopLValues();
        // If we can't find a possible invariant, no point proceeding.
        if (loopVariablePossibilities.isEmpty()) {
            logger.info("No loop variable possibilities\n");
            return;
        }

        BlockIdentifier whileBlockIdentifier = whileStatement.getBlockIdentifier();
        // For each of the back calling targets, find a CONSTANT inc/dec
        // * which is in the loop arena
        // * before any instruction which has multiple parents.
        Set<LValue> mutatedPossibilities = null;
        for (Op03SimpleStatement source : backSources) {
            Set<LValue> incrPoss = findForInvariants(source, whileBlockIdentifier);
            if (mutatedPossibilities == null) {
                mutatedPossibilities = incrPoss;
            } else {
                mutatedPossibilities.retainAll(incrPoss);
            }
            // If there are no possibilites, then we can't do anything.
            if (mutatedPossibilities.isEmpty()) {
                logger.info("No invariant possibilities on source\n");
                return;
            }
        }
        if (mutatedPossibilities == null || mutatedPossibilities.isEmpty()) {
            logger.info("No invariant intersection\n");
            return;
        }
        loopVariablePossibilities.retainAll(mutatedPossibilities);
        // Intersection between incremented / tested.
        if (loopVariablePossibilities.isEmpty()) {
            logger.info("No invariant intersection\n");
            return;
        }

        // If we've got choices, ignore currently.
        if (loopVariablePossibilities.size() > 1) {
            logger.info("Multiple invariant intersection\n");
            return;
        }

        LValue loopVariable = loopVariablePossibilities.iterator().next();

        /*
         * Now, go back and get the list of mutations.  Make sure they're all equivalent, then nop them out.
         */
        List<Op03SimpleStatement> mutations = ListFactory.newList();
        for (Op03SimpleStatement source : backSources) {
            Op03SimpleStatement incrStatement = getForInvariant(source, loopVariable, whileBlockIdentifier);
            mutations.add(incrStatement);
        }

        Op03SimpleStatement baseline = mutations.get(0);
        for (Op03SimpleStatement incrStatement : mutations) {
            // Compare - they all have to mutate in the same way.
            if (!baseline.equals(incrStatement)) {
                logger.info("Incompatible constant mutations.");
                return;
            }
        }

        //
        // If possible, go back and find an unconditional assignment to the loop variable.
        // We have to be sure that moving this to the for doesn't violate SSA versions.
        //
        Op03SimpleStatement initialValue = findMovableAssignment(statement, loopVariable);
        AssignmentSimple initalAssignmentSimple = null;

        if (initialValue != null) {
            initalAssignmentSimple = (AssignmentSimple) initialValue.containedStatement;
            initialValue.nopOut();
        }

        AbstractAssignment updateAssignment = (AbstractAssignment) baseline.containedStatement;
        for (Op03SimpleStatement incrStatement : mutations) {
            incrStatement.nopOut();
        }
        whileBlockIdentifier.setBlockType(BlockType.FORLOOP);
        whileStatement.replaceWithForLoop(initalAssignmentSimple, updateAssignment.getInliningExpression());

        for (Op03SimpleStatement source : backSources) {
            if (source.containedInBlocks.contains(whileBlockIdentifier)) {
                /*
                 * Loop at anything which jumps directly to here.
                 */
                List<Op03SimpleStatement> ssources = ListFactory.newList(source.getSources());
                for (Op03SimpleStatement ssource : ssources) {
                    if (ssource.containedInBlocks.contains(whileBlockIdentifier)) {
                        Statement sstatement = ssource.getStatement();
                        if (sstatement instanceof JumpingStatement) {
                            JumpingStatement jumpingStatement = (JumpingStatement) sstatement;
                            if (jumpingStatement.getJumpTarget().getContainer() == source) {
                                ((JumpingStatement) sstatement).setJumpType(JumpType.CONTINUE);
                                ssource.replaceTarget(source, statement);
                                statement.addSource(ssource);
                                source.removeSource(ssource);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void rewriteWhilesAsFors(List<Op03SimpleStatement> statements) {
        // Find all the while loops beginnings.
        List<Op03SimpleStatement> whileStarts = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return (in.containedStatement instanceof WhileStatement) && ((WhileStatement) in.containedStatement).getBlockIdentifier().getBlockType() == BlockType.WHILELOOP;
            }
        });

        for (Op03SimpleStatement whileStart : whileStarts) {
            rewriteWhileAsFor(whileStart, statements);
        }
    }

    private static void rewriteDoWhileTruePredAsWhile(Op03SimpleStatement end, List<Op03SimpleStatement> statements) {
        WhileStatement whileStatement = (WhileStatement) end.getStatement();
        if (null != whileStatement.getCondition()) return;

        /*
         * The first statement inside this loop needs to be a test which breaks out of the loop.
         */
        List<Op03SimpleStatement> endTargets = end.getTargets();
        if (endTargets.size() != 1) return;

        Op03SimpleStatement loopStart = endTargets.get(0);
        Statement loopBodyStartStatement = loopStart.getStatement();
        /*
         * loopBodyStartStatement is NOT the do Statement, but is the first statement.
         * We need to search its sources to find the DO statement, and verify that it's the
         * correct one for the block.
         */
        BlockIdentifier whileBlockIdentifier = whileStatement.getBlockIdentifier();

        Op03SimpleStatement doStart = null;
        for (Op03SimpleStatement source : loopStart.getSources()) {
            Statement statement = source.getStatement();
            if (statement.getClass() == DoStatement.class) {
                DoStatement doStatement = (DoStatement) statement;
                if (doStatement.getBlockIdentifier() == whileBlockIdentifier) {
                    doStart = source;
                    break;
                }
            }
        }
        if (doStart == null) return;

        /* Now - is the loopBodyStartStatement a conditional?
         * If it's a direct jump, we can just target the while statement.
         */
        if (loopBodyStartStatement.getClass() == IfStatement.class) {
            return; // Not handled yet.
        } else if (loopBodyStartStatement.getClass() == IfExitingStatement.class) {
            IfExitingStatement ifExitingStatement = (IfExitingStatement) loopBodyStartStatement;
            Statement exitStatement = ifExitingStatement.getExitStatement();
            ConditionalExpression conditionalExpression = ifExitingStatement.getCondition();
            WhileStatement replacementWhile = new WhileStatement(conditionalExpression.getNegated(), whileBlockIdentifier);
            GotoStatement endGoto = new GotoStatement();
            endGoto.setJumpType(JumpType.CONTINUE);
            end.replaceStatement(endGoto);
            Op03SimpleStatement after = new Op03SimpleStatement(doStart.getBlockIdentifiers(), exitStatement, end.getIndex().justAfter());
            statements.add(statements.indexOf(end) + 1, after);
            doStart.addTarget(after);
            after.addSource(doStart);
            doStart.replaceStatement(replacementWhile);
            /*
             * Replace everything that pointed at loopStart with a pointer to doStart.
             */
            Op03SimpleStatement afterLoopStart = loopStart.getTargets().get(0);
            doStart.replaceTarget(loopStart, afterLoopStart);
            afterLoopStart.replaceSource(loopStart, doStart);
            loopStart.removeSource(doStart);
            loopStart.removeTarget(afterLoopStart);
            for (Op03SimpleStatement otherSource : loopStart.getSources()) {
                otherSource.replaceTarget(loopStart, doStart);
                doStart.addSource(otherSource);
            }
            loopStart.getSources().clear();
            loopStart.nopOut();
            whileBlockIdentifier.setBlockType(BlockType.WHILELOOP);
            return;
        } else {
            return;
        }
    }

    public static void rewriteDoWhileTruePredAsWhile(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> doWhileEnds = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return (in.containedStatement instanceof WhileStatement) && ((WhileStatement) in.containedStatement).getBlockIdentifier().getBlockType() == BlockType.UNCONDITIONALDOLOOP;
            }
        });

        if (doWhileEnds.isEmpty()) return;

        for (Op03SimpleStatement whileEnd : doWhileEnds) {
            rewriteDoWhileTruePredAsWhile(whileEnd, statements);
        }
    }

    public static void rewriteBreakStatements(List<Op03SimpleStatement> statements) {
        test:
        for (Op03SimpleStatement statement : statements) {
            Statement innerStatement = statement.getStatement();
            if (innerStatement instanceof JumpingStatement) {
                JumpingStatement jumpingStatement = (JumpingStatement) innerStatement;
                // 
                // If there's a goto, see if it goes OUT of a known while loop, OR
                // if it goes back to the comparison statement for a known while loop.
                // 
                if (jumpingStatement.getJumpType().isUnknown()) {
                    Statement targetInnerStatement = jumpingStatement.getJumpTarget();
                    Op03SimpleStatement targetStatement = (Op03SimpleStatement) targetInnerStatement.getContainer();
                    // TODO : Should we be checking if this is a 'breakable' block?
                    if (targetStatement.thisComparisonBlock != null) {
                        BlockType blockType = targetStatement.thisComparisonBlock.getBlockType();
                        switch (blockType) {
                            default: // hack, figuring out.
                                // Jumps to the comparison test of a WHILE
                                // Continue loopBlock, IF this statement is INSIDE that block.
                                if (BlockIdentifier.blockIsOneOf(targetStatement.thisComparisonBlock, statement.containedInBlocks)) {
                                    jumpingStatement.setJumpType(JumpType.CONTINUE);
                                    continue test;
                                }
                                ;
                        }
                    }
                    if (targetStatement.getBlockStarted() != null &&
                            targetStatement.getBlockStarted().getBlockType() == BlockType.UNCONDITIONALDOLOOP) {
                        if (BlockIdentifier.blockIsOneOf(targetStatement.getBlockStarted(), statement.containedInBlocks)) {
                            jumpingStatement.setJumpType(JumpType.CONTINUE);
                            continue test;
                        }
                    }
                    if (!targetStatement.immediatelyAfterBlocks.isEmpty()) {
                        BlockIdentifier outermostContainedIn = BlockIdentifier.getOutermostContainedIn(targetStatement.immediatelyAfterBlocks, statement.containedInBlocks);
                        // Break to the outermost block.
                        if (outermostContainedIn != null) {
                            jumpingStatement.setJumpType(JumpType.BREAK);
                            continue test;
                        }
                    }
                }
            }
        }
    }


    /*
     * Attempt to determine if a goto is jumping over catch blocks - if it is, we can mark it as a GOTO_OUT_OF_TRY
     * (the same holds for a goto inside a catch, we use the same marker).
     */
    private static boolean classifyTryCatchLeaveGoto(Op03SimpleStatement gotoStm, Set<BlockIdentifier> blocks, int idx, Set<BlockIdentifier> tryBlockIdents, Map<BlockIdentifier, Op03SimpleStatement> tryStatementsByBlock, Map<BlockIdentifier, List<BlockIdentifier>> catchStatementByBlock, List<Op03SimpleStatement> in) {
        if (idx >= in.size() - 1) return false;

        GotoStatement gotoStatement = (GotoStatement) gotoStm.getStatement();

        Set<BlockIdentifier> tryBlocks = SetUtil.intersectionOrNull(blocks, tryBlockIdents);
        if (tryBlocks == null) return false;


        Op03SimpleStatement after = in.get(idx + 1);
        Set<BlockIdentifier> afterBlocks = SetUtil.intersectionOrNull(after.getBlockIdentifiers(), tryBlockIdents);

        if (afterBlocks != null) tryBlocks.removeAll(afterBlocks);
        if (tryBlocks.size() != 1) return false;
        BlockIdentifier left = tryBlocks.iterator().next();

        // Ok, so we've jumped out of exactly one try block.  But where have we jumped to?  Is it to directly after
        // a catch block for that try block?
        Op03SimpleStatement tryStatement = tryStatementsByBlock.get(left);
        if (tryStatement == null) return false;

        List<BlockIdentifier> catchForThis = catchStatementByBlock.get(left);
        if (catchForThis == null) return false;

        /*
         * We require that gotoStm's one target is
         * /not in 'left'/
         * just after a catch block.
         * Not in any of the catch blocks.
         */
        Op03SimpleStatement gotoTgt = gotoStm.getTargets().get(0);
        Set<BlockIdentifier> gotoTgtIdents = gotoTgt.getBlockIdentifiers();
        if (SetUtil.hasIntersection(gotoTgtIdents, catchForThis)) return false;
        int idxtgt = in.indexOf(gotoTgt);
        if (idxtgt == 0) return false;
        Op03SimpleStatement prev = in.get(idxtgt - 1);
        if (!SetUtil.hasIntersection(prev.getBlockIdentifiers(), catchForThis)) return false;
        // YAY!
        gotoStatement.setJumpType(JumpType.GOTO_OUT_OF_TRY);
        return true;
    }

    private static boolean classifyTryLeaveGoto(Op03SimpleStatement gotoStm, int idx, Set<BlockIdentifier> tryBlockIdents, Map<BlockIdentifier, Op03SimpleStatement> tryStatementsByBlock, Map<BlockIdentifier, List<BlockIdentifier>> catchStatementByBlock, List<Op03SimpleStatement> in) {
        Set<BlockIdentifier> blocks = gotoStm.getBlockIdentifiers();
        return classifyTryCatchLeaveGoto(gotoStm, blocks, idx, tryBlockIdents, tryStatementsByBlock, catchStatementByBlock, in);
    }

    private static boolean classifyCatchLeaveGoto(Op03SimpleStatement gotoStm, int idx, Set<BlockIdentifier> tryBlockIdents, Map<BlockIdentifier, Op03SimpleStatement> tryStatementsByBlock, Map<BlockIdentifier, List<BlockIdentifier>> catchStatementByBlock, Map<BlockIdentifier, Set<BlockIdentifier>> catchBlockToTryBlocks, List<Op03SimpleStatement> in) {
        Set<BlockIdentifier> inBlocks = gotoStm.getBlockIdentifiers();

        /*
         * Map blocks to the union of the TRY blocks we're in catch blocks of.
         */
        Set<BlockIdentifier> blocks = SetFactory.newOrderedSet();
        for (BlockIdentifier block : inBlocks) {
            //
            // In case it's a lazy map, 2 stage lookup and fetch.
            if (catchBlockToTryBlocks.containsKey(block)) {
                Set<BlockIdentifier> catchToTries = catchBlockToTryBlocks.get(block);
                blocks.addAll(catchToTries);
            }
        }

        return classifyTryCatchLeaveGoto(gotoStm, blocks, idx, tryBlockIdents, tryStatementsByBlock, catchStatementByBlock, in);
    }


    public static boolean classifyGotos(List<Op03SimpleStatement> in) {
        boolean result = false;
        List<Pair<Op03SimpleStatement, Integer>> gotos = ListFactory.newList();
        Map<BlockIdentifier, Op03SimpleStatement> tryStatementsByBlock = MapFactory.newMap();
        Map<BlockIdentifier, List<BlockIdentifier>> catchStatementsByBlock = MapFactory.newMap();
        Map<BlockIdentifier, Set<BlockIdentifier>> catchToTries = MapFactory.newLazyMap(new UnaryFunction<BlockIdentifier, Set<BlockIdentifier>>() {
            @Override
            public Set<BlockIdentifier> invoke(BlockIdentifier arg) {
                return SetFactory.newOrderedSet();
            }
        });
        for (int x = 0, len = in.size(); x < len; ++x) {
            Op03SimpleStatement stm = in.get(x);
            Statement statement = stm.getStatement();
            Class<?> clz = statement.getClass();
            if (clz == TryStatement.class) {
                TryStatement tryStatement = (TryStatement) statement;
                BlockIdentifier tryBlockIdent = tryStatement.getBlockIdentifier();
                tryStatementsByBlock.put(tryBlockIdent, stm);
                List<Op03SimpleStatement> targets = stm.getTargets();
                List<BlockIdentifier> catchBlocks = ListFactory.newList();
                catchStatementsByBlock.put(tryStatement.getBlockIdentifier(), catchBlocks);
                for (int y = 1, len2 = targets.size(); y < len2; ++y) {
                    Statement statement2 = targets.get(y).getStatement();
                    if (statement2.getClass() == CatchStatement.class) {
                        BlockIdentifier catchBlockIdent = ((CatchStatement) statement2).getCatchBlockIdent();
                        catchBlocks.add(catchBlockIdent);
                        catchToTries.get(catchBlockIdent).add(tryBlockIdent);
                    }
                }
            } else if (clz == GotoStatement.class) {
                GotoStatement gotoStatement = (GotoStatement) statement;
                if (gotoStatement.getJumpType().isUnknown()) {
                    gotos.add(Pair.make(stm, x));
                }
            }
        }
        /*
         * Pass over try statements.  If there aren't any, don't bother.
         */
        if (!tryStatementsByBlock.isEmpty()) {
            for (Pair<Op03SimpleStatement, Integer> goto_ : gotos) {
                Op03SimpleStatement stm = goto_.getFirst();
                int idx = goto_.getSecond();
                if (classifyTryLeaveGoto(stm, idx, tryStatementsByBlock.keySet(), tryStatementsByBlock, catchStatementsByBlock, in) ||
                        classifyCatchLeaveGoto(stm, idx, tryStatementsByBlock.keySet(), tryStatementsByBlock, catchStatementsByBlock, catchToTries, in)) {
                    result = true;
                }
            }
        }

        return result;
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

    // Find simple loops.
    // Identify distinct set of backjumps (b1,b2), which jump back to somewhere (p) which has a forward
    // jump to somewhere which is NOT a /DIRECT/ parent of the backjumps (i.e. has to go through p)
    // p must be a direct parent of all of (b1,b2)
    public static void identifyLoops1(Method method, List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory) {
        // Find back references.
        // Verify that they belong to jump instructions (otherwise something has gone wrong)
        // (if, goto).

        List<Op03SimpleStatement> pathtests = Functional.filter(statements, new TypeFilter<GotoStatement>(GotoStatement.class));
        for (Op03SimpleStatement start : pathtests) {
            considerAsPathologicalLoop(start, statements);
        }

        List<Op03SimpleStatement> backjumps = Functional.filter(statements, new HasBackJump());
        List<Op03SimpleStatement> starts = Functional.uniqAll(Functional.map(backjumps, new GetBackJump()));
        /* Each of starts is the target of a back jump.
         * Consider each of these seperately, and for each of these verify
         * that it contains a forward jump to something which is not a parent except through p.
         */
        Map<BlockIdentifier, Op03SimpleStatement> blockEndsCache = MapFactory.newMap();
        Collections.sort(starts, new CompareByIndex());

        List<LoopResult> loopResults = ListFactory.newList();
        Set<BlockIdentifier> relevantBlocks = SetFactory.newSet();
        for (Op03SimpleStatement start : starts) {
            BlockIdentifier blockIdentifier = considerAsWhileLoopStart(method, start, statements, blockIdentifierFactory, blockEndsCache);
            if (blockIdentifier == null) {
                blockIdentifier = considerAsDoLoopStart(start, statements, blockIdentifierFactory, blockEndsCache);
            }
            if (blockIdentifier != null) {
                loopResults.add(new LoopResult(blockIdentifier, start));
                relevantBlocks.add(blockIdentifier);
            }
        }

        if (loopResults.isEmpty()) return;
        Collections.reverse(loopResults);
        /*
         * If we have any overlapping but not nested loops, that's because the earlier one(s)
         * are not properly exited, just continued over (see LoopTest48).
         *
         * Need to extend loop bodies and transform whiles into continues.
         */
        fixLoopOverlaps(statements, loopResults, relevantBlocks);
    }

    /* For each block, if the start is inside other blocks, but the last backjump is
     * NOT, then we need to convert the last backjump into a continue / conditional continue,
     * and add a while (true). :P
     */
    private static void fixLoopOverlaps(List<Op03SimpleStatement> statements, List<LoopResult> loopResults, Set<BlockIdentifier> relevantBlocks) {

        Map<BlockIdentifier, List<BlockIdentifier>> requiredExtents = MapFactory.newLazyMap(new UnaryFunction<BlockIdentifier, List<BlockIdentifier>>() {
            @Override
            public List<BlockIdentifier> invoke(BlockIdentifier arg) {
                return ListFactory.newList();
            }
        });

        Map<BlockIdentifier, Op03SimpleStatement> lastForBlock = MapFactory.newMap();

        for (LoopResult loopResult : loopResults) {
            final Op03SimpleStatement start = loopResult.blockStart;
            final BlockIdentifier testBlockIdentifier = loopResult.blockIdentifier;

            Set<BlockIdentifier> startIn = SetUtil.intersectionOrNull(start.getBlockIdentifiers(), relevantBlocks);
            List<Op03SimpleStatement> backSources = Functional.filter(start.sources, new Predicate<Op03SimpleStatement>() {
                @Override
                public boolean test(Op03SimpleStatement in) {
                    return in.getBlockIdentifiers().contains(testBlockIdentifier) &&
                            in.getIndex().isBackJumpTo(start);
                }
            });

            if (backSources.isEmpty()) continue;
            Collections.sort(backSources, new CompareByIndex());
            Op03SimpleStatement lastBackSource = backSources.get(backSources.size() - 1);
            /*
             * If start is in a relevantBlock that an end isn't, then THAT BLOCK needs to be extended to at least the end
             * of testBlockIdentifier.
             */
            lastForBlock.put(testBlockIdentifier, lastBackSource);
            if (startIn == null) continue;

            Set<BlockIdentifier> backIn = SetUtil.intersectionOrNull(lastBackSource.getBlockIdentifiers(), relevantBlocks);
            if (backIn == null) continue;
            if (!backIn.containsAll(startIn)) {
                // NB Not ordered - will this bite me?  Shouldn't.
                Set<BlockIdentifier> startMissing = SetFactory.newSet(startIn);
                startMissing.removeAll(backIn);
                for (BlockIdentifier missing : startMissing) {
                    requiredExtents.get(missing).add(testBlockIdentifier);
                }
            }
        }

        if (requiredExtents.isEmpty()) return;

        // RequiredExtents[key] should be extended to the last of VALUE.
        List<BlockIdentifier> extendBlocks = ListFactory.newList(requiredExtents.keySet());
        Collections.sort(extendBlocks, new Comparator<BlockIdentifier>() {
            @Override
            public int compare(BlockIdentifier blockIdentifier, BlockIdentifier blockIdentifier2) {
                return blockIdentifier.getIndex() - blockIdentifier2.getIndex();  // reverse order.
            }
        });

        Comparator<Op03SimpleStatement> comparator = new CompareByIndex();

        // NB : we're deliberately not using key ordering.
        for (BlockIdentifier extendThis : extendBlocks) {
            List<BlockIdentifier> possibleEnds = requiredExtents.get(extendThis);
            if (possibleEnds.isEmpty()) continue;
            // Find last block.
            // We re-fetch because we might have updated the possibleEndOps.
            List<Op03SimpleStatement> possibleEndOps = ListFactory.newList();
            for (BlockIdentifier end : possibleEnds) {
                possibleEndOps.add(lastForBlock.get(end));
            }
            Collections.sort(possibleEndOps, comparator);

            Op03SimpleStatement extendTo = possibleEndOps.get(possibleEndOps.size() - 1);
            /*
             * Need to extend block 'extendThis' to 'extendTo'.
             * We also need to rewrite any terminal backjumps as continues, and
             * add a 'while true' (or block to that effect).
             */
            Op03SimpleStatement oldEnd = lastForBlock.get(extendThis);

            int start = statements.indexOf(oldEnd);
            int end = statements.indexOf(extendTo);

            for (int x = start; x <= end; ++x) {
                statements.get(x).getBlockIdentifiers().add(extendThis);
            }
            // Rewrite oldEnd appropriately.  Leave end of block dangling, we will fix it later.
            rewriteEndLoopOverlapStatement(oldEnd, extendThis);
        }
    }

    private static void rewriteEndLoopOverlapStatement(Op03SimpleStatement oldEnd, BlockIdentifier loopBlock) {
        Statement statement = oldEnd.getStatement();
        Class<?> clazz = statement.getClass();
        if (clazz == WhileStatement.class) {
            WhileStatement whileStatement = (WhileStatement) statement;
            ConditionalExpression condition = whileStatement.getCondition();
            if (oldEnd.targets.size() == 2) {
                IfStatement repl = new IfStatement(condition);
                repl.setKnownBlocks(loopBlock, null);
                repl.setJumpType(JumpType.CONTINUE);
                oldEnd.replaceStatement(repl);
                if (oldEnd.thisComparisonBlock == loopBlock) {
                    oldEnd.thisComparisonBlock = null;
                }
                return;
            } else if (oldEnd.targets.size() == 1 && condition == null) {
                GotoStatement repl = new GotoStatement();
                repl.setJumpType(JumpType.CONTINUE);
                oldEnd.replaceStatement(repl);
                if (oldEnd.thisComparisonBlock == loopBlock) {
                    oldEnd.thisComparisonBlock = null;
                }
                return;
            }

        } else {
            int x = 1;
        }
    }

    private static class HasBackJump implements Predicate<Op03SimpleStatement> {
        @Override
        public boolean test(Op03SimpleStatement in) {
            InstrIndex inIndex = in.getIndex();
            List<Op03SimpleStatement> targets = in.getTargets();
            for (Op03SimpleStatement target : targets) {
                if (target.getIndex().compareTo(inIndex) <= 0) {
                    if (!(in.containedStatement instanceof JumpingStatement)) {
                        if (in.containedStatement instanceof JSRRetStatement ||
                                in.containedStatement instanceof WhileStatement) {
                            return false;
                        }
                        throw new ConfusedCFRException("Invalid back jump on " + in.containedStatement);
                    } else {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static class GetBackJump implements UnaryFunction<Op03SimpleStatement, Op03SimpleStatement> {
        @Override
        public Op03SimpleStatement invoke(Op03SimpleStatement in) {
            InstrIndex inIndex = in.getIndex();
            List<Op03SimpleStatement> targets = in.getTargets();
            for (Op03SimpleStatement target : targets) {
                if (target.getIndex().compareTo(inIndex) <= 0) {
                    return target;
                }
            }
            throw new ConfusedCFRException("No back index.");
        }
    }

    private static Op03SimpleStatement findFirstConditional(Op03SimpleStatement start) {
        Set<Op03SimpleStatement> visited = SetFactory.newSet();
        do {
            Statement innerStatement = start.getStatement();
            if (innerStatement instanceof IfStatement) {
                return start;
            }
            List<Op03SimpleStatement> targets = start.getTargets();
            if (targets.size() != 1) return null;
            start = targets.get(0);
            if (visited.contains(start)) {
                return null;
            }
            visited.add(start);
        } while (start != null);
        return null;
    }


    /*
     * If we have
     *
     * if (.... ) goto x
     * FFFFFF
     * goto Y
     *
     * and the instruction BEFORE Y does not have Y as its direct predecessor, we can push FFF through.
     *
     * Why do we want to do this?  Because if X is directly after Y, we might get to the point where we end up as
     *
     * if (..... ) goto x
     * goto y
     * X
     *
     * Which can be converted into a negative jump.
     *
     * We only do this for linear statements, we'd need a structured transform to do something better.
     * (at op4 stage).
     */
    public static List<Op03SimpleStatement> pushThroughGoto(Method method, List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> pathtests = Functional.filter(statements, new ExactTypeFilter<GotoStatement>(GotoStatement.class));
        boolean success = false;
        for (Op03SimpleStatement gotostm : pathtests) {
            if (gotostm.getTargets().get(0).getIndex().isBackJumpTo(gotostm)) {
                if (pushThroughGoto(method, gotostm, statements)) {
                    success = true;
                }
            }
        }
        if (success) {
            statements = Op03SimpleStatement.renumber(statements);
            // This is being done twice deliberately.  Should rewrite rewriteNegativeJumps to iterate.
            // in practice 2ce is fine.
            // see /com/db4o/internal/btree/BTreeNode.class
            Op03SimpleStatement.rewriteNegativeJumps(statements);
            Op03SimpleStatement.rewriteNegativeJumps(statements);
        }
        return statements;
    }

    private static boolean moveable(Statement statement) {
        Class<?> clazz = statement.getClass();
        if (clazz == Nop.class) return true;
        if (clazz == AssignmentSimple.class) return true;
        if (clazz == CommentStatement.class) return true;
        if (clazz == ExpressionStatement.class) return true;
        return false;
    }

    private static boolean pushThroughGoto(Method method, Op03SimpleStatement forwardGoto, List<Op03SimpleStatement> statements) {

        if (forwardGoto.sources.size() != 1) return false;

        final Op03SimpleStatement tgt = forwardGoto.getTargets().get(0);
        int idx = statements.indexOf(tgt);
        if (idx == 0) return false;
        final Op03SimpleStatement before = statements.get(idx - 1);
        if (tgt.getSources().contains(before)) return false;

        InstrIndex beforeTgt = tgt.getIndex().justBefore();
        Op03SimpleStatement last = forwardGoto;

        /*
         * We can't push through a goto if TGT is the first instruction after a loop body.
         */
        class IsLoopBlock implements Predicate<BlockIdentifier> {
            @Override
            public boolean test(BlockIdentifier in) {
                BlockType blockType = in.getBlockType();
                switch (blockType) {
                    case WHILELOOP:
                    case DOLOOP:
                        return true;
                }
                return false;
            }
        }
        IsLoopBlock isLoopBlock = new IsLoopBlock();
        Set<BlockIdentifier> beforeLoopBlocks = SetFactory.newSet(Functional.filter(before.getBlockIdentifiers(), isLoopBlock));
        Set<BlockIdentifier> tgtLoopBlocks = SetFactory.newSet(Functional.filter(tgt.getBlockIdentifiers(), isLoopBlock));
        if (!beforeLoopBlocks.equals(tgtLoopBlocks)) return false;


        before.getBlockIdentifiers();

        class IsExceptionBlock implements Predicate<BlockIdentifier> {
            @Override
            public boolean test(BlockIdentifier in) {
                BlockType blockType = in.getBlockType();
                switch (blockType) {
                    case TRYBLOCK:
                    case SWITCH:
                    case CATCHBLOCK:
                    case CASE:
                        return true;
                }
                return false;
            }
        }
        Predicate<BlockIdentifier> exceptionFilter = new IsExceptionBlock();

        Set<BlockIdentifier> exceptionBlocks = SetFactory.newSet(Functional.filter(tgt.getBlockIdentifiers(), exceptionFilter));
        int nextCandidateIdx = statements.indexOf(forwardGoto) - 1;

        Op03SimpleStatement lastTarget = tgt;
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        boolean success = false;
        while (true) {
            Op03SimpleStatement tryMoveThis = forwardGoto.sources.get(0);

            if (!moveable(tryMoveThis.getStatement())) return success;

            if (!seen.add(tryMoveThis)) return success;

            if (statements.get(nextCandidateIdx) != tryMoveThis) return success;
            if (tryMoveThis.targets.size() != 1) return success;
            if (tryMoveThis.sources.size() != 1) return success;
            Op03SimpleStatement beforeTryMove = tryMoveThis.sources.get(0);
            // Is it in the same exception blocks?
            Set<BlockIdentifier> moveEB = SetFactory.newSet(Functional.filter(forwardGoto.getBlockIdentifiers(), exceptionFilter));
            if (!moveEB.equals(exceptionBlocks)) return success;
            /* Move this instruction through the goto
             */
            beforeTryMove.replaceTarget(tryMoveThis, forwardGoto);
            forwardGoto.replaceSource(tryMoveThis, beforeTryMove);

            forwardGoto.replaceTarget(lastTarget, tryMoveThis);
            tryMoveThis.replaceSource(beforeTryMove, forwardGoto);

            tryMoveThis.replaceTarget(forwardGoto, lastTarget);
            lastTarget.replaceSource(forwardGoto, tryMoveThis);

            tryMoveThis.index = beforeTgt;
            beforeTgt = beforeTgt.justBefore();

            tryMoveThis.containedInBlocks.clear();
            tryMoveThis.containedInBlocks.addAll(lastTarget.containedInBlocks);
            tryMoveThis.immediatelyAfterBlocks.clear();
            tryMoveThis.immediatelyAfterBlocks.addAll(lastTarget.immediatelyAfterBlocks);
            lastTarget.immediatelyAfterBlocks.clear();
            lastTarget = tryMoveThis;
            nextCandidateIdx--;
            success = true;
        }
    }

    /*
     * Eclipse has a nasty habit of instead of emitting
     *
     * test : if (a >= 5) goto after
     * ..
     * ..
     * ..
     * ++a;
     * goto test
     * after:
     *
     * emitting this -
     *
     * (a) goto test
     * body :
     * ..
     * ..
     * ..
     * ++a
     * (b) test: if (a < 5) goto body
     * (c) after :
     *
     * We identify this as (a) an unconditional forward jump, to a comparison (b)
     * which jumps directly to the instruction after the forward jump.
     *
     * All other sources for the comparison (b) must be in the range [body, test).
     *
     * If this is the case, replace (a) with negated (b), which jumps on success to (c).
     * replace (b) with an unconditional jump to a.
     */
    public static void eclipseLoopPass(List<Op03SimpleStatement> statements) {
        boolean effect = false;
        for (int x = 0, len = statements.size() - 1; x < len; ++x) {
            Op03SimpleStatement statement = statements.get(x);
            Statement inr = statement.getStatement();
            if (inr.getClass() != GotoStatement.class) continue;

            Op03SimpleStatement target = statement.getTargets().get(0);
            if (target == statement) continue; // hey, paranoia.

            if (target.getIndex().isBackJumpFrom(statement)) continue;
            Statement tgtInr = target.getStatement();
            if (tgtInr.getClass() != IfStatement.class) continue;
            IfStatement ifStatement = (IfStatement) tgtInr;

            Op03SimpleStatement bodyStart = statements.get(x + 1);
            if (bodyStart != ifStatement.getJumpTarget().getContainer()) continue;

            for (Op03SimpleStatement source : target.getSources()) {
                InstrIndex sourceIdx = source.getIndex();
                if (sourceIdx.isBackJumpFrom(statement) ||
                        sourceIdx.isBackJumpTo(target)) continue;
            }
            Op03SimpleStatement afterTest = target.getTargets().get(0);
//            // This has to be a fall through
//            if (statements.indexOf(afterTest) != statements.indexOf(target) + 1) continue;

            // OK - we're in the right boat!
            IfStatement topTest = new IfStatement(ifStatement.getCondition().getNegated().simplify());
            statement.replaceStatement(topTest);
            statement.replaceTarget(target, bodyStart);
            bodyStart.addSource(statement);
            statement.addTarget(afterTest);
            afterTest.replaceSource(target, statement);
            target.replaceStatement(new Nop());
            target.removeSource(statement);
            target.removeTarget(afterTest);
            target.replaceTarget(bodyStart, statement);
            target.replaceStatement(new GotoStatement());
            bodyStart.removeSource(target);
            statement.addSource(target);

            effect = true;
        }

        if (effect) {
            Op03SimpleStatement.removePointlessJumps(statements);
        }
    }


    private static class GraphVisitorReachableInThese implements BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>> {
        private final Set<Integer> reachable;
        private final Map<Op03SimpleStatement, Integer> instrToIdx;

        public GraphVisitorReachableInThese(Set<Integer> reachable, Map<Op03SimpleStatement, Integer> instrToIdx) {
            this.reachable = reachable;
            this.instrToIdx = instrToIdx;
        }

        @Override
        public void call(Op03SimpleStatement node, GraphVisitor<Op03SimpleStatement> graphVisitor) {
            Integer idx = instrToIdx.get(node);
            if (idx == null) return;
            reachable.add(idx);
            for (Op03SimpleStatement target : node.targets) {
                graphVisitor.enqueue(target);
            }
        }
    }

    /*
     * To handle special case tricksiness.
     */
    private static boolean considerAsPathologicalLoop(final Op03SimpleStatement start, List<Op03SimpleStatement> statements) {
        if (start.containedStatement.getClass() != GotoStatement.class) return false;
        if (start.targets.get(0) != start) return false;
        Op03SimpleStatement next = new Op03SimpleStatement(start.getBlockIdentifiers(), new GotoStatement(), start.getIndex().justAfter());
        start.replaceStatement(new CommentStatement("Infinite loop"));
        start.replaceTarget(start, next);
        start.replaceSource(start, next);
        next.addSource(start);
        next.addTarget(start);
        statements.add(statements.indexOf(start) + 1, next);
        return true;
    }

    private static class LoopResult {
        final BlockIdentifier blockIdentifier;
        final Op03SimpleStatement blockStart;

        private LoopResult(BlockIdentifier blockIdentifier, Op03SimpleStatement blockStart) {
            this.blockIdentifier = blockIdentifier;
            this.blockStart = blockStart;
        }
    }

    private static BlockIdentifier considerAsDoLoopStart(final Op03SimpleStatement start, final List<Op03SimpleStatement> statements,
                                                         BlockIdentifierFactory blockIdentifierFactory,
                                                         Map<BlockIdentifier, Op03SimpleStatement> postBlockCache) {

        final InstrIndex startIndex = start.getIndex();
        List<Op03SimpleStatement> backJumpSources = start.getSources();
        if (backJumpSources.isEmpty()) {
            throw new ConfusedCFRException("Node doesn't have ANY sources! " + start);
        }
        backJumpSources = Functional.filter(backJumpSources, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getIndex().compareTo(startIndex) >= 0;
            }
        });
        Collections.sort(backJumpSources, new CompareByIndex());
        if (backJumpSources.isEmpty()) {
            throw new ConfusedCFRException("Node should have back jump sources.");
        }
        Op03SimpleStatement lastJump = backJumpSources.get(backJumpSources.size() - 1);
        boolean conditional = false;
        if (lastJump.containedStatement instanceof IfStatement) {
            conditional = true;
            IfStatement ifStatement = (IfStatement) lastJump.containedStatement;
            if (ifStatement.getJumpTarget().getContainer() != start) {
                return null;
            }
        }
//        if (!conditional) return false;

        int startIdx = statements.indexOf(start);
        int endIdx = statements.indexOf(lastJump);

        if (startIdx >= endIdx) return null;

        BlockIdentifier blockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(conditional ? BlockType.DOLOOP : BlockType.UNCONDITIONALDOLOOP);

        /* Given that the potential statements inside this block are idxConditional+1 -> idxAfterEnd-1, [a->b]
        * there SHOULD be a prefix set (or all) in here which is addressable from idxConditional+1 without leaving the
        * range [a->b].  Determine this.  If we have reachable entries which aren't in the prefix, we can't cope.
        */
        try {
            validateAndAssignLoopIdentifier(statements, startIdx, endIdx + 1, blockIdentifier, start);
        } catch (CannotPerformDecode e) {
            // Can't perform this optimisation.
            return null;
        }


        // Add a 'do' statement infront of the block (which does not belong to the block)
        // transform the test to a 'POST_WHILE' statement.
        Op03SimpleStatement doStatement = new Op03SimpleStatement(start.containedInBlocks, new DoStatement(blockIdentifier), start.index.justBefore());
        doStatement.containedInBlocks.remove(blockIdentifier);
        // we need to link the do statement in between all the sources of start WHICH
        // are NOT in blockIdentifier.
        List<Op03SimpleStatement> startSources = ListFactory.newList(start.sources);
        for (Op03SimpleStatement source : startSources) {
            if (!source.containedInBlocks.contains(blockIdentifier)) {
                source.replaceTarget(start, doStatement);
                start.removeSource(source);
                doStatement.addSource(source);
            }
        }
        doStatement.addTarget(start);
        start.addSource(doStatement);
        Op03SimpleStatement postBlock;
        if (conditional) {
            postBlock = lastJump.getTargets().get(0);
        } else {
            /*
             * The best we can do is know it's a fall through to whatever WOULD have happened.
             */
            int newIdx = statements.indexOf(lastJump) + 1;
            if (newIdx >= statements.size()) {
                postBlock = new Op03SimpleStatement(SetFactory.<BlockIdentifier>newSet(), new ReturnNothingStatement(), lastJump.getIndex().justAfter());
                statements.add(postBlock);

//                return false;
//                throw new ConfusedCFRException("Unconditional while with break but no following statement.");
            } else {
                postBlock = statements.get(newIdx);
            }
        }

        if (start.firstStatementInThisBlock != null) {
            /* We need to figure out if this new loop is inside or outside block started at start.
             *
             */
            BlockIdentifier outer = findOuterBlock(start.firstStatementInThisBlock, blockIdentifier, statements);
            if (blockIdentifier == outer) {
                // Ok, we're the new outer
                throw new UnsupportedOperationException();
            } else {
                // we're the new inner.  We need to change start to be first in US, and make US first of what start was
                // in.
                doStatement.firstStatementInThisBlock = start.firstStatementInThisBlock;
                start.firstStatementInThisBlock = blockIdentifier;
            }
        }

        /*
         * One final very grotty 'optimisation' - if there are any try blocks that began INSIDE the
         * loop (so present in the last statement, but not in the first), shuffle the lastJump until it
         * is after them.
         *
         * This (seems) to only apply to unconditionals.
         */
        shuntLoop:
        if (!conditional) {
            Set<BlockIdentifier> lastContent = SetFactory.newSet(lastJump.getBlockIdentifiers());
            lastContent.removeAll(start.getBlockIdentifiers());
            Set<BlockIdentifier> internalTryBlocks = SetFactory.newOrderedSet(Functional.filter(lastContent, new Predicate<BlockIdentifier>() {
                @Override
                public boolean test(BlockIdentifier in) {
                    return in.getBlockType() == BlockType.TRYBLOCK;
                }
            }));
            // internalTryBlocks represents try blocks which started AFTER the loop did.
            if (internalTryBlocks.isEmpty()) break shuntLoop;

            final int postBlockIdx = statements.indexOf(postBlock);
            int lastPostBlock = postBlockIdx;
            innerShutLoop:
            do {
                if (lastPostBlock + 1 >= statements.size()) break innerShutLoop;

                int currentIdx = lastPostBlock + 1;
                Op03SimpleStatement stm = statements.get(lastPostBlock);
                if (!(stm.getStatement() instanceof CatchStatement)) break innerShutLoop;

                CatchStatement catchStatement = (CatchStatement) stm.getStatement();
                BlockIdentifier catchBlockIdent = catchStatement.getCatchBlockIdent();
                List<BlockIdentifier> tryBlocks = Functional.map(catchStatement.getExceptions(), new UnaryFunction<ExceptionGroup.Entry, BlockIdentifier>() {
                    @Override
                    public BlockIdentifier invoke(ExceptionGroup.Entry arg) {
                        return arg.getTryBlockIdentifier();
                    }
                });
                if (!internalTryBlocks.containsAll(tryBlocks)) break innerShutLoop;
                while (currentIdx < statements.size() - 1 && statements.get(currentIdx).getBlockIdentifiers().contains(catchBlockIdent)) {
                    currentIdx++;
                }
                lastPostBlock = currentIdx;
            } while (true);
            if (lastPostBlock != postBlockIdx) {
                final Op03SimpleStatement afterNewJump = statements.get(lastPostBlock);
                // Find after statement.  Insert a jump forward (out) here, and then insert
                // a synthetic lastJump.  The previous lastJump should now jump to our synthetic
                // We can insert a BACK jump to lastJump's target
                //
                Op03SimpleStatement newBackJump = new Op03SimpleStatement(afterNewJump.getBlockIdentifiers(), new GotoStatement(), afterNewJump.getIndex().justBefore());
                newBackJump.addTarget(start);
                newBackJump.addSource(lastJump);
                lastJump.replaceTarget(start, newBackJump);
                start.replaceSource(lastJump, newBackJump);
                /*
                 * If the instruction we're being placed infront of has a direct precedent, then that needs to get transformed
                 * into a goto (actually a break, but that will happen later).  Otherwise, it will be fine.
                 */
                Op03SimpleStatement preNewJump = statements.get(lastPostBlock - 1);
                if (afterNewJump.getSources().contains(preNewJump)) {
                    Op03SimpleStatement interstit = new Op03SimpleStatement(preNewJump.getBlockIdentifiers(), new GotoStatement(), newBackJump.getIndex().justBefore());
                    preNewJump.replaceTarget(afterNewJump, interstit);
                    afterNewJump.replaceSource(preNewJump, interstit);
                    interstit.addSource(preNewJump);
                    interstit.addTarget(afterNewJump);
                    statements.add(lastPostBlock, interstit);
                    lastPostBlock++;
                }

                statements.add(lastPostBlock, newBackJump);
                lastJump = newBackJump;
                postBlock = afterNewJump;
                /*
                 * Now mark everything we just walked into the loop body.
                 */
                for (int idx = postBlockIdx; idx <= lastPostBlock; ++idx) {
                    statements.get(idx).markBlock(blockIdentifier);
                }
            }
        }

        statements.add(statements.indexOf(start), doStatement);
        lastJump.markBlockStatement(blockIdentifier, null, lastJump, statements);
        start.markFirstStatementInBlock(blockIdentifier);


        postBlock.markPostBlock(blockIdentifier);
        postBlockCache.put(blockIdentifier, postBlock);

        return blockIdentifier;
    }

    private static BlockIdentifier findOuterBlock(BlockIdentifier b1, BlockIdentifier b2, List<Op03SimpleStatement> statements) {
        for (Op03SimpleStatement s : statements) {
            Set<BlockIdentifier> contained = s.getBlockIdentifiers();
            if (contained.contains(b1)) {
                if (!contained.contains(b2)) {
                    return b1;
                }
            } else if (contained.contains(b2)) {
                return b2;
            }
        }
        /*
         * Can't decide!  Have to choose outer.  ??
         */
        return b1;
    }

    /* Is the first conditional jump NOT one of the sources of start?
    * Take the target of the first conditional jump - is it somehwhere which is not reachable from
    * any of the forward sources of start without going through start?
    *
    * If so we've probably got a for/while loop.....
    * decode both as a while loop, we can convert it into a for later.
    */
    private static BlockIdentifier considerAsWhileLoopStart(final Method method,
                                                            final Op03SimpleStatement start, final List<Op03SimpleStatement> statements,
                                                            BlockIdentifierFactory blockIdentifierFactory,
                                                            Map<BlockIdentifier, Op03SimpleStatement> postBlockCache) {
        final InstrIndex startIndex = start.getIndex();
        List<Op03SimpleStatement> backJumpSources = start.getSources();
        backJumpSources = Functional.filter(backJumpSources, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getIndex().compareTo(startIndex) >= 0;
            }
        });
        Collections.sort(backJumpSources, new CompareByIndex());
        Op03SimpleStatement conditional = findFirstConditional(start);
        if (conditional == null) {
            // No conditional before we have a branch?  Probably a do { } while. 
            logger.info("Can't find a conditional");
            return null;
        }
        // Now we've found our first conditional before a branch - is the target AFTER the last backJump?
        // Requires Debuggered conditionals.
        // TODO : ORDERING
        Op03SimpleStatement lastJump = backJumpSources.get(backJumpSources.size() - 1);
        /* Conditional has 2 targets - one of which has to NOT be a parent of 'sources', unless
         * it involves going through conditional the other way.
         */
        List<Op03SimpleStatement> conditionalTargets = conditional.getTargets();
        /*
         * This could be broken by an obfuscator easily.  We need a transform state which
         * normalises the code so the jump out is the explicit jump.
         * TODO : Could do this by finding which one of the targets of the condition is NOT reachable
         * TODO : by going back from each of the backJumpSources to conditional
         *
         * TODO: This might give us something WAY past the end of the loop, if the next instruction is to
         * jump past a catch block.....
         */
        Op03SimpleStatement loopBreak = conditionalTargets.get(1);

        /*
         * One very special case here - if the conditional is an EXPLICIT self-loop.
         * In which case the taken branch isn't the exit, it's the loop.
         *
         * Rewrote as
         * x :  if (cond) goto x+2
         * x+1 : goto x
         * x+2
         *
         */
        if (loopBreak == conditional && start == conditional) {
            Op03SimpleStatement backJump = new Op03SimpleStatement(conditional.getBlockIdentifiers(), new GotoStatement(), conditional.getIndex().justAfter());
            Op03SimpleStatement notTaken = conditional.targets.get(0);
            conditional.replaceTarget(notTaken, backJump);
            conditional.replaceSource(conditional, backJump);
            conditional.replaceTarget(conditional, notTaken);
            backJump.addSource(conditional);
            backJump.addTarget(conditional);
            statements.add(statements.indexOf(conditional) + 1, backJump);
            conditionalTargets = conditional.getTargets();
            loopBreak = notTaken;
        }

        if (loopBreak.getIndex().compareTo(lastJump.getIndex()) <= 0) {
            // The conditional doesn't take us to after the last back jump, i.e. it's not a while {} loop.
            // ... unless it's an inner while loop continuing to a prior loop.
            if (loopBreak.getIndex().compareTo(startIndex) >= 0) {
                return null;
            }
        }

        if (start != conditional) {
            // We'll have problems - there are actions taken inside the conditional.
            return null;
        }
        int idxConditional = statements.indexOf(start);

        /* If this loop has a test at the bottom, we may have a continue style exit, i.e. the loopBreak
         * is not just reachable from the top.  We can find this by seeing if loopBreak is reachable from
         * any of the backJumpSources, without going through start.
         *
         * OR we may just have a do { } while....
         */
        /* Take the statement which directly preceeds loopbreak
         * TODO : ORDERCHEAT
         * and verify that it's reachable from conditional, WITHOUT going through start.
         * If so, we guess that it's the end of the loop.
         */
        int idxAfterEnd = statements.indexOf(loopBreak);
        if (idxAfterEnd < idxConditional) {
            /*
             * We've got an inner loop which is terminating back to the start of the outer loop.
             * This means we have to figure out the body of the loop by considering back jumps.
             * We can't rely on the last statement in the loop being a backjump to the start, as it
             * may be a continue/break to an outer loop.
             */
            /* We probably need a while block between start and the END of the loop which begins at idxEnd.
             * (if that exists.)
             */
            Op03SimpleStatement startOfOuterLoop = statements.get(idxAfterEnd);
            if (startOfOuterLoop.thisComparisonBlock == null) {
                // Boned.
                return null;
            }
            // Find the END of this block.
            Op03SimpleStatement endOfOuter = postBlockCache.get(startOfOuterLoop.thisComparisonBlock);
            if (endOfOuter == null) {
                throw new ConfusedCFRException("BlockIdentifier doesn't exist in blockEndsCache");
            }
            idxAfterEnd = statements.indexOf(endOfOuter);
        }

        /* TODO : ORDERCHEAT */
        // Mark instructions in the list between start and maybeEndLoop as being in this block.
        if (idxConditional >= idxAfterEnd) {
//            throw new ConfusedCFRException("Can't decode block");
            return null;
        }
        BlockIdentifier blockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.WHILELOOP);

        /* Given that the potential statements inside this block are idxConditional+1 -> idxAfterEnd-1, [a->b]
        * there SHOULD be a prefix set (or all) in here which is addressable from idxConditional+1 without leaving the
        * range [a->b].  Determine this.  If we have reachable entries which aren't in the prefix, we can't cope.
        */
        int lastIdx;
        try {
            lastIdx = validateAndAssignLoopIdentifier(statements, idxConditional + 1, idxAfterEnd, blockIdentifier, start);
        } catch (CannotPerformDecode e) {
            return null;
        }

        Op03SimpleStatement lastInBlock = statements.get(lastIdx);
        Op03SimpleStatement blockEnd = statements.get(idxAfterEnd);
        //
        start.markBlockStatement(blockIdentifier, lastInBlock, blockEnd, statements);
        statements.get(idxConditional + 1).markFirstStatementInBlock(blockIdentifier);
        blockEnd.markPostBlock(blockIdentifier);
        postBlockCache.put(blockIdentifier, blockEnd);
        /*
         * is the end of the while loop jumping to something which is NOT directly after it?  If so, we need to introduce
         * an intermediate jump.
         */
        Op03SimpleStatement afterLastInBlock = (lastIdx + 1) < statements.size() ? statements.get(lastIdx + 1) : null;
        loopBreak = conditional.getTargets().get(1);
        if (afterLastInBlock != loopBreak) {
            Op03SimpleStatement newAfterLast = new Op03SimpleStatement(afterLastInBlock.getBlockIdentifiers(), new GotoStatement(), lastInBlock.getIndex().justAfter());
            conditional.replaceTarget(loopBreak, newAfterLast);
            newAfterLast.addSource(conditional);
            loopBreak.replaceSource(conditional, newAfterLast);
            newAfterLast.addTarget(loopBreak);
            statements.add(newAfterLast);
        }

        return blockIdentifier;
    }

    private static int getFarthestReachableInRange(List<Op03SimpleStatement> statements, int start, int afterEnd) {
        Map<Op03SimpleStatement, Integer> instrToIdx = MapFactory.newMap();
        for (int x = start; x < afterEnd; ++x) {
            Op03SimpleStatement statement = statements.get(x);
            instrToIdx.put(statement, x);
        }

        Set<Integer> reachableNodes = SetFactory.newSortedSet();
        GraphVisitorReachableInThese graphVisitorCallee = new GraphVisitorReachableInThese(reachableNodes, instrToIdx);
        GraphVisitor<Op03SimpleStatement> visitor = new GraphVisitorDFS<Op03SimpleStatement>(statements.get(start), graphVisitorCallee);
        visitor.process();

        final int first = start;
        int last = -1;
        boolean foundLast = false;

        for (int x = first; x < afterEnd; ++x) {
            if (reachableNodes.contains(x) || statements.get(x).isNop()) {
                if (foundLast) {
//                    return afterEnd - 1;
                    throw new CannotPerformDecode("reachable test BLOCK was exited and re-entered.");
                }
            } else {
                if (!foundLast) {
                    last = x - 1;
                }
                foundLast = true;
            }
        }
        if (last == -1) last = afterEnd - 1;
        return last;

    }

    private static int validateAndAssignLoopIdentifier(List<Op03SimpleStatement> statements, int idxTestStart, int idxAfterEnd, BlockIdentifier blockIdentifier, Op03SimpleStatement start) {
        int last = getFarthestReachableInRange(statements, idxTestStart, idxAfterEnd);

        /*
         * What if the last back jump was inside a catch statement?  Find catch statements which exist at
         * last, but not in start - we have to extend the loop to the end of the catch statements....
         * and change it to a while (false).
         */
        Op03SimpleStatement discoveredLast = statements.get(last);
        Set<BlockIdentifier> lastBlocks = SetFactory.newSet(discoveredLast.containedInBlocks);
        lastBlocks.removeAll(start.getBlockIdentifiers());
        Set<BlockIdentifier> catches = SetFactory.newSet(Functional.filter(lastBlocks, new Predicate<BlockIdentifier>() {
            @Override
            public boolean test(BlockIdentifier in) {
                return (in.getBlockType() == BlockType.CATCHBLOCK);
            }
        }));
        int newlast = last;
        while (!catches.isEmpty()) {
            /*
             * Need to find the rest of these catch blocks, and add them to the range.
             */
            Op03SimpleStatement stm = statements.get(newlast);
            catches.retainAll(stm.getBlockIdentifiers());
            if (catches.isEmpty()) {
                break;
            }
            last = newlast;
            if (newlast < statements.size() - 1) {
                newlast++;
            } else {
                break;
            }
        }


        for (int x = idxTestStart; x <= last; ++x) {
            statements.get(x).markBlock(blockIdentifier);
        }
        return last;
    }

    private static class IsForwardIf implements Predicate<Op03SimpleStatement> {
        @Override
        public boolean test(Op03SimpleStatement in) {
            if (!(in.containedStatement instanceof IfStatement)) return false;
            IfStatement ifStatement = (IfStatement) in.containedStatement;
            if (!ifStatement.getJumpType().isUnknown()) return false;
            if (in.targets.get(1).index.compareTo(in.index) <= 0) return false;
            return true;
        }
    }

    private JumpType getJumpType() {
        if (containedStatement instanceof JumpingStatement) {
            return ((JumpingStatement) containedStatement).getJumpType();
        }
        return JumpType.NONE;
    }

    private static void markWholeBlock(List<Op03SimpleStatement> statements, BlockIdentifier blockIdentifier) {
        Op03SimpleStatement start = statements.get(0);
        start.markFirstStatementInBlock(blockIdentifier);
        for (Op03SimpleStatement statement : statements) {
            statement.markBlock(blockIdentifier);
        }
    }

    private static class DiscoveredTernary {
        LValue lValue;
        Expression e1;
        Expression e2;

        private DiscoveredTernary(LValue lValue, Expression e1, Expression e2) {
            this.lValue = lValue;
            this.e1 = e1;
            this.e2 = e2;
        }

        private static Troolean isOneOrZeroLiteral(Expression e) {
            if (!(e instanceof Literal)) return Troolean.NEITHER;
            TypedLiteral typedLiteral = ((Literal) e).getValue();
            Object value = typedLiteral.getValue();
            if (!(value instanceof Integer)) return Troolean.NEITHER;
            int iValue = (Integer) value;
            if (iValue == 1) return Troolean.TRUE;
            if (iValue == 0) return Troolean.FALSE;
            return Troolean.NEITHER;
        }

        private boolean isPointlessBoolean() {
            if (!(e1.getInferredJavaType().getRawType() == RawJavaType.BOOLEAN &&
                    e2.getInferredJavaType().getRawType() == RawJavaType.BOOLEAN)) return false;

            if (isOneOrZeroLiteral(e1) != Troolean.TRUE) return false;
            if (isOneOrZeroLiteral(e2) != Troolean.FALSE) return false;
            return true;
        }
    }

    public static class TypeFilter<T> implements Predicate<Op03SimpleStatement> {
        private final Class<T> clazz;
        private final boolean positive;

        public TypeFilter(Class<T> clazz) {
            this.clazz = clazz;
            this.positive = true;
        }

        public TypeFilter(Class<T> clazz, boolean positive) {
            this.clazz = clazz;
            this.positive = positive;
        }

        @Override
        public boolean test(Op03SimpleStatement in) {
            return (positive == clazz.isInstance(in.containedStatement));
        }
    }

    public static class ExactTypeFilter<T> implements Predicate<Op03SimpleStatement> {
        private final Class<T> clazz;
        private final boolean positive;

        public ExactTypeFilter(Class<T> clazz) {
            this.clazz = clazz;
            this.positive = true;
        }

        public ExactTypeFilter(Class<T> clazz, boolean positive) {
            this.clazz = clazz;
            this.positive = positive;
        }

        @Override
        public boolean test(Op03SimpleStatement in) {
            return (positive == (clazz == (in.containedStatement.getClass())));
        }
    }

    private static DiscoveredTernary testForTernary(List<Op03SimpleStatement> ifBranch, List<Op03SimpleStatement> elseBranch, Op03SimpleStatement leaveIfBranch) {
        if (ifBranch == null || elseBranch == null) return null;
        if (leaveIfBranch == null) return null;
        TypeFilter<Nop> notNops = new TypeFilter<Nop>(Nop.class, false);
        ifBranch = Functional.filter(ifBranch, notNops);
        switch (ifBranch.size()) {
            case 1:
                break;
            case 2:
                if (ifBranch.get(1) != leaveIfBranch) {
                    return null;
                }
                break;
            default:
                return null;
        }
        elseBranch = Functional.filter(elseBranch, notNops);
        if (elseBranch.size() != 1) {
            return null;
        }

        Op03SimpleStatement s1 = ifBranch.get(0);
        Op03SimpleStatement s2 = elseBranch.get(0);
        if (s2.sources.size() != 1) return null;
        LValue l1 = s1.containedStatement.getCreatedLValue();
        LValue l2 = s2.containedStatement.getCreatedLValue();
        if (l1 == null || l2 == null) {
            return null;
        }
        if (!l2.equals(l1)) {
            return null;
        }
        return new DiscoveredTernary(l1, s1.containedStatement.getRValue(), s2.containedStatement.getRValue());
    }


    private static boolean considerAsTrivialIf(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory, Set<Op03SimpleStatement> ignoreTheseJumps) {
        Op03SimpleStatement takenTarget = ifStatement.targets.get(1);
        Op03SimpleStatement notTakenTarget = ifStatement.targets.get(0);
        int idxTaken = statements.indexOf(takenTarget);
        int idxNotTaken = statements.indexOf(notTakenTarget);
        if (idxTaken != idxNotTaken + 1) return false;
        if (!(takenTarget.getStatement().getClass() == GotoStatement.class &&
                notTakenTarget.getStatement().getClass() == GotoStatement.class &&
                takenTarget.targets.get(0) == notTakenTarget.targets.get(0))) {
            return false;
        }
        notTakenTarget.replaceStatement(new CommentStatement("empty if block"));
        // Replace the not taken target with an 'empty if statement'.

        return false;
    }

    /*
     * Spot a rather perverse structure that only happens with DEX2Jar, as far as I can tell.
     *
     * if (x) goto b
     * (a:)
     * ....[1] [ LINEAR STATEMENTS (* or already discounted) ]
     * goto c
     * b:
     * ....[2] [ LINEAR STATEMENTS (*) ]
     * goto d
     * c:
     * ....[3]
     *
     * Can be replaced with
     *
     * if (!x) goto a
     * b:
     * ...[2]
     * goto d
     * a:
     * ....[1]
     * c:
     * ....[3]
     *
     * Which, in turn, may allow us to make some more interesting choices later.
     */
    private static boolean considerAsDexIf(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory, Set<Op03SimpleStatement> ignoreTheseJumps) {
        Statement innerStatement = ifStatement.getStatement();
        if (innerStatement.getClass() != IfStatement.class) {
            return false;
        }
        IfStatement innerIfStatement = (IfStatement) innerStatement;

        int startIdx = statements.indexOf(ifStatement);
        int bidx = statements.indexOf(ifStatement.getTargets().get(1));
        if (bidx <= startIdx) return false; // shouldn't happen.
        InstrIndex startIndex = ifStatement.getIndex();
        InstrIndex bIndex = ifStatement.getTargets().get(1).getIndex();
        if (startIndex.compareTo(bIndex) >= 0) {
            return false; // likewise.  Indicates we need a renumber.
        }

        int aidx = startIdx + 1;

        int cidx = findOverIdx(bidx, statements);
        if (cidx == -1) return false;

        int didx = findOverIdx(cidx, statements);
        if (didx == -1) return false;

        if (didx <= cidx) return false;

        Set<Op03SimpleStatement> permittedSources = SetFactory.newSet(ifStatement);
        if (!isRangeOnlyReachable(aidx, bidx, cidx, statements, permittedSources)) return false;
        if (!isRangeOnlyReachable(bidx, cidx, didx, statements, permittedSources)) return false;

        /*
         * Looks like a legitimate reordering - rewrite statements.  Pick up the entire block, and
         * move as per above.
         */
        List<Op03SimpleStatement> alist = statements.subList(aidx, bidx);
        List<Op03SimpleStatement> blist = statements.subList(bidx, cidx);

        /*
         * Nop out the last entry of alist.
         */
        alist.get(alist.size() - 1).nopOut();

        List<Op03SimpleStatement> ifTargets = ifStatement.getTargets();
        // Swap targets.
        Op03SimpleStatement tgtA = ifTargets.get(0);
        Op03SimpleStatement tgtB = ifTargets.get(1);
        ifTargets.set(0, tgtB);
        ifTargets.set(1, tgtA);
        innerIfStatement.setCondition(innerIfStatement.getCondition().getNegated().simplify());

        List<Op03SimpleStatement> acopy = ListFactory.newList(alist);
        blist.addAll(acopy);
        alist = statements.subList(aidx, bidx);
        alist.clear();

        reindexInPlace(statements);

        return true;
    }

    private static int findOverIdx(int startNext, List<Op03SimpleStatement> statements) {
        /*
         * Find a forward goto before b.
         */
        Op03SimpleStatement next = statements.get(startNext);

        Op03SimpleStatement cStatement = null;
        for (int gSearch = startNext - 1; gSearch >= 0; gSearch--) {
            Op03SimpleStatement stm = statements.get(gSearch);
            Statement s = stm.getStatement();
            if (s instanceof Nop) continue;
            if (s.getClass() == GotoStatement.class) {
                Op03SimpleStatement tgtC = stm.getTargets().get(0);
                if (tgtC.getIndex().isBackJumpFrom(next)) return -1;
                cStatement = tgtC;
                break;
            }
            return -1;
        }
        if (cStatement == null) return -1;
        int cidx = statements.indexOf(cStatement);
        return cidx;
    }

    /*
     * Is this structured as
     *
     * ..
     * ..
     * ..
     * goto X
     *
     * Where the range is self-contained.
     */
    private static boolean isRangeOnlyReachable(int startIdx, int endIdx, int tgtIdx, List<Op03SimpleStatement> statements, Set<Op03SimpleStatement> permittedSources) {

        Set<Op03SimpleStatement> reachable = SetFactory.newSet();
        final Op03SimpleStatement startStatement = statements.get(startIdx);
        final Op03SimpleStatement endStatement = statements.get(endIdx);
        final Op03SimpleStatement tgtStatement = statements.get(tgtIdx);
        InstrIndex startIndex = startStatement.getIndex();
        InstrIndex endIndex = endStatement.getIndex();
        InstrIndex finalTgtIndex = tgtStatement.getIndex();

        reachable.add(statements.get(startIdx));
        boolean foundEnd = false;
        for (int idx = startIdx; idx < endIdx; ++idx) {
            Op03SimpleStatement stm = statements.get(idx);
            if (!reachable.contains(stm)) {
                return false;
            }
            // We don't expect this statement to have sources before startIndex or after bIndex.
            // We don't expect any raw gotos ( or targets ) after bIndex / beforeStartIndex. (break / continue are ok).
            for (Op03SimpleStatement source : stm.getSources()) {
                InstrIndex sourceIndex = source.getIndex();
                if (sourceIndex.compareTo(startIndex) < 0) {
                    if (!permittedSources.contains(source)) {
                        return false;
                    }
                }
                if (sourceIndex.compareTo(endIndex) >= 0) {
                    return false;
                }
            }
            for (Op03SimpleStatement target : stm.getTargets()) {
                InstrIndex tgtIndex = target.getIndex();
                if (tgtIndex.compareTo(startIndex) < 0) {
                    return false;
                }
                if (tgtIndex.compareTo(endIndex) >= 0) {
                    if (tgtIndex == finalTgtIndex) {
                        foundEnd = true;
                    } else {
                        return false;
                    }
                }
                reachable.add(target);
            }
        }
        return foundEnd;
    }

    /*
    * This is an if statement where both targets are forward.
    *
    * it's a 'simple' if, if:
    *
    * target[0] reaches (incl) the instruction before target[1] without any jumps (other than continue / break).
    *
    * note that the instruction before target[1] doesn't have to have target[1] as a target...
    * (we might have if (foo) return;)
    *
    * If it's a SIMPLE if/else, then the last statement of the if block is a goto, which jumps to after the else
    * block.  We don't want to keep that goto, as we've inferred structure now.
    *
    * We trim that GOTO when we move from an UnstructuredIf to a StructuredIf.
    */
    private static boolean considerAsSimpleIf(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory, Set<Op03SimpleStatement> ignoreTheseJumps) {
        Op03SimpleStatement takenTarget = ifStatement.targets.get(1);
        Op03SimpleStatement notTakenTarget = ifStatement.targets.get(0);
        int idxTaken = statements.indexOf(takenTarget);
        int idxNotTaken = statements.indexOf(notTakenTarget);
        IfStatement innerIfStatement = (IfStatement) ifStatement.containedStatement;

        Set<Op03SimpleStatement> ignoreLocally = SetFactory.newSet();

        boolean takenAction = false;

        int idxCurrent = idxNotTaken;
        if (idxCurrent > idxTaken) return false;

        int idxEnd = idxTaken;
        int maybeElseEndIdx = -1;
        Op03SimpleStatement maybeElseEnd = null;
        boolean maybeSimpleIfElse = false;
        boolean extractCommonEnd = false;
        GotoStatement leaveIfBranchGoto = null;
        Op03SimpleStatement leaveIfBranchHolder = null;
        List<Op03SimpleStatement> ifBranch = ListFactory.newList();
        List<Op03SimpleStatement> elseBranch = null;
        // Consider the try blocks we're in at this point.  (the ifStatemenet).
        // If we leave any of them, we've left the if.
        Set<BlockIdentifier> blocksAtStart = ifStatement.containedInBlocks;
        if (idxCurrent == idxEnd) {
            // It's a trivial tautology? We can't nop it out unless it's side effect free.
            // Instead insert a comment.
            Op03SimpleStatement taken = new Op03SimpleStatement(blocksAtStart, new CommentStatement("empty if block"), notTakenTarget.index.justBefore());
            taken.addSource(ifStatement);
            taken.addTarget(notTakenTarget);
            Op03SimpleStatement emptyTarget = ifStatement.targets.get(0);
            if (notTakenTarget != emptyTarget) {
                notTakenTarget.addSource(taken);
            }
            emptyTarget.replaceSource(ifStatement, taken);
            ifStatement.targets.set(0, taken);
            statements.add(idxTaken, taken);

            BlockIdentifier ifBlockLabel = blockIdentifierFactory.getNextBlockIdentifier(BlockType.SIMPLE_IF_TAKEN);
            taken.markFirstStatementInBlock(ifBlockLabel);
            // O(N) insertion here is annoying, but we need to fix up the list IMMEDIATELY.
            taken.getBlockIdentifiers().add(ifBlockLabel);
            innerIfStatement.setKnownBlocks(ifBlockLabel, null);
            innerIfStatement.setJumpType(JumpType.GOTO_OUT_OF_IF);
            return true;
        }
        Set<Op03SimpleStatement> validForwardParents = SetFactory.newSet();
        validForwardParents.add(ifStatement);
        /*
         * Find the (possible) end of the if block, which is a forward unconditional jump.
         * If that's the case, cache it and rewrite any jumps we see which go to the same place
         * as double jumps via this unconditional.
         */
        Op03SimpleStatement stmtLastBlock = statements.get(idxTaken - 1);
        Op03SimpleStatement stmtLastBlockRewrite = null;
        Statement stmtLastBlockInner = stmtLastBlock.getStatement();
        if (stmtLastBlockInner.getClass() == GotoStatement.class) {
            stmtLastBlockRewrite = stmtLastBlock;
        }

        do {
            Op03SimpleStatement statementCurrent = statements.get(idxCurrent);
            /* Consider sources of this which jumped forward to get to it.
             *
             */
            InstrIndex currentIndex = statementCurrent.getIndex();
            for (Op03SimpleStatement source : statementCurrent.sources) {
                if (currentIndex.isBackJumpTo(source)) {
                    if (!validForwardParents.contains(source)) {
                        // source from outside the block.  This likely means that we've actually left the block.
                        // eg
                        // if (foo) goto Z
                        // ....
                        // return 1;
                        // label:
                        // statement <-- here.
                        // ...
                        // Z
                        //
                        // (although it might mean some horrid duffs device style compiler output).
                        // TODO: CheckForDuff As below.
                        //if (statementIsReachableFrom(statementCurrent, ifStatement)) return false;
                        Op03SimpleStatement newJump = new Op03SimpleStatement(ifStatement.containedInBlocks, new GotoStatement(), statementCurrent.getIndex().justBefore());
                        if (statementCurrent != ifStatement.targets.get(0)) {
                            Op03SimpleStatement oldTarget = ifStatement.targets.get(1);
                            newJump.addTarget(oldTarget);
                            newJump.addSource(ifStatement);
                            ifStatement.replaceTarget(oldTarget, newJump);
                            oldTarget.replaceSource(ifStatement, newJump);
                            statements.add(idxCurrent, newJump);
                            return true;
                        }
                    }
                }
            }
            validForwardParents.add(statementCurrent);

            ifBranch.add(statementCurrent);
            JumpType jumpType = statementCurrent.getJumpType();
            if (jumpType.isUnknown() && !ignoreTheseJumps.contains(statementCurrent)) {
                // Todo : Currently we can only cope with hitting
                // the last jump as an unknown jump.  We ought to be able to rewrite
                // i.e. if we have
                /*
                    if (list != null) goto lbl6;
                    System.out.println("A");
                    if (set != null) goto lbl8; <<-----
                    System.out.println("B");
                    goto lbl8;
                    lbl6:
                    ELSE BLOCK
                    lbl8:
                    return true;
                 */
                // this is a problem, because the highlighted statement will cause us to abandon processing.
                if (idxCurrent == idxTaken - 1) {
                    Statement mGotoStatement = statementCurrent.containedStatement;
                    if (!(mGotoStatement.getClass() == GotoStatement.class)) return false;
                    GotoStatement gotoStatement = (GotoStatement) mGotoStatement;
                    // It's unconditional, and it's a forward jump.
                    maybeElseEnd = statementCurrent.getTargets().get(0);
                    maybeElseEndIdx = statements.indexOf(maybeElseEnd);
                    if (maybeElseEnd.getIndex().compareTo(takenTarget.getIndex()) <= 0) {
                        return false;
                    }
                    leaveIfBranchHolder = statementCurrent;
                    leaveIfBranchGoto = gotoStatement;
                    maybeSimpleIfElse = true;
                } else {
                    if (stmtLastBlockRewrite == null) {
                        Op03SimpleStatement tgtContainer = statementCurrent.getTargets().get(0);
                        if (tgtContainer == takenTarget) {
                            // We can ignore this statement in future passes.
                            idxCurrent++;
                            continue;
                        }

                        return false;
                    }
                    // We can try to rewrite this block to have an indirect jump via the end of the block,
                    // if that's appropriate.
                    List<Op03SimpleStatement> targets = statementCurrent.getTargets();

                    Op03SimpleStatement eventualTarget = stmtLastBlockRewrite.getTargets().get(0);
                    boolean found = false;
                    for (int x = 0; x < targets.size(); ++x) {
                        Op03SimpleStatement target = targets.get(x);
                        if (target == eventualTarget && target != stmtLastBlockRewrite) {
                            // Equivalent to
                            // statementCurrent.replaceTarget(eventualTarget, stmtLastBlockRewrite);
                            targets.set(x, stmtLastBlockRewrite);
                            stmtLastBlockRewrite.addSource(statementCurrent);
                            // Todo : I don't believe the latter branch will EVER happen.
                            if (eventualTarget.sources.contains(stmtLastBlockRewrite)) {
                                eventualTarget.removeSource(statementCurrent);
                            } else {
                                eventualTarget.replaceSource(statementCurrent, stmtLastBlockRewrite);
                            }

                            found = true;
                        }
                    }

                    return found;
                }
            }
            idxCurrent++;
        } while (idxCurrent != idxEnd);
        // We've reached the "other" branch of the conditional.
        // If maybeSimpleIfElse is set, then there was a final jump to

        if (maybeSimpleIfElse) {
            // If there is a NO JUMP path between takenTarget and maybeElseEnd, then that's the ELSE block
            elseBranch = ListFactory.newList();
            idxCurrent = idxTaken;
            idxEnd = maybeElseEndIdx;
            do {
                Op03SimpleStatement statementCurrent = statements.get(idxCurrent);
                elseBranch.add(statementCurrent);
                JumpType jumpType = statementCurrent.getJumpType();
                if (jumpType.isUnknown()) {
                    /* We allow ONE unconditional forward jump, to maybeElseEnd.  If we find this, we have
                     * a simple if /else/ block, which we can rewrite as
                     * if (a) { .. .goto X } else { .... goto X } -->
                     * if (a) { ... } else { ....} ; goto X
                     */
                    Statement mGotoStatement = statementCurrent.containedStatement;
                    if (!(mGotoStatement.getClass() == GotoStatement.class)) return false;
                    GotoStatement gotoStatement = (GotoStatement) mGotoStatement;
                    // It's unconditional, and it's a forward jump.
                    if (statementCurrent.targets.get(0) == maybeElseEnd) {
                        idxEnd = idxCurrent;
                        idxCurrent--;


                        // We can do this aggressively, as it doesn't break the graph.
                        leaveIfBranchHolder.replaceTarget(maybeElseEnd, statementCurrent);
                        statementCurrent.addSource(leaveIfBranchHolder);
                        maybeElseEnd.removeSource(leaveIfBranchHolder);
                        elseBranch.remove(statementCurrent);   // eww.
                        takenAction = true;
                    } else {
                        return false;
                    }
                }
                idxCurrent++;
            } while (idxCurrent != idxEnd);
        }

        Op03SimpleStatement realEnd = statements.get(idxEnd);
        Set<BlockIdentifier> blocksAtEnd = realEnd.containedInBlocks;
        if (!(blocksAtStart.containsAll(blocksAtEnd) && blocksAtEnd.size() == blocksAtStart.size())) {
            return takenAction;
        }

        // It's an if statement / simple if/else, for sure.  Can we replace it with a ternary?
        DiscoveredTernary ternary = testForTernary(ifBranch, elseBranch, leaveIfBranchHolder);
        if (ternary != null) {
            // We can ditch this whole thing for a ternary expression.
            for (Op03SimpleStatement statement : ifBranch) statement.nopOut();
            for (Op03SimpleStatement statement : elseBranch) statement.nopOut();
            // todo : do I need to do a more complex merge?
            ifStatement.ssaIdentifiers = leaveIfBranchHolder.ssaIdentifiers;

            // We need to be careful we don't replace x ? 1 : 0 with x here unless we're ABSOLUTELY
            // sure that the final expression type is boolean.....
            // this may come back to bite. (at which point we can inject a (? 1 : 0) ternary...
            ConditionalExpression conditionalExpression = innerIfStatement.getCondition().getNegated().simplify();
            Expression rhs = ternary.isPointlessBoolean() ?
                    conditionalExpression :
                    new TernaryExpression(
                            conditionalExpression,
                            ternary.e1, ternary.e2);

            ifStatement.replaceStatement(
                    new AssignmentSimple(
                            ternary.lValue,
                            rhs
                    )
            );
            // Reduce the ternary lValue's created location count, if we can.
            if (ternary.lValue instanceof StackSSALabel) {
                StackSSALabel stackSSALabel = (StackSSALabel) ternary.lValue;
                StackEntry stackEntry = stackSSALabel.getStackEntry();
                stackEntry.decSourceCount();
//                List<Long> sources = stackEntry.getSources();
//                stackEntry.removeSource(sources.get(sources.size() - 1));
            }

            // If statement now should have only one target.
            List<Op03SimpleStatement> tmp = ListFactory.uniqueList(ifStatement.targets);
            ifStatement.targets.clear();
            ifStatement.targets.addAll(tmp);
            if (ifStatement.targets.size() != 1) {
                throw new ConfusedCFRException("If statement should only have one target after dedup");
            }
            Op03SimpleStatement joinStatement = ifStatement.targets.get(0);
            tmp = ListFactory.uniqueList(joinStatement.sources);
            joinStatement.sources.clear();
            joinStatement.sources.addAll(tmp);

            /*
             * And now we need to reapply LValue condensing in the region! :(
             */
            condenseLValues(statements);

            return true;
        }


        BlockIdentifier ifBlockLabel = blockIdentifierFactory.getNextBlockIdentifier(BlockType.SIMPLE_IF_TAKEN);
        markWholeBlock(ifBranch, ifBlockLabel);
        BlockIdentifier elseBlockLabel = null;
        if (maybeSimpleIfElse) {
            elseBlockLabel = blockIdentifierFactory.getNextBlockIdentifier(BlockType.SIMPLE_IF_ELSE);
            if (elseBranch.isEmpty()) {
                elseBlockLabel = null;
                maybeSimpleIfElse = false;
                //throw new IllegalStateException();
            } else {
                markWholeBlock(elseBranch, elseBlockLabel);
            }
        }

        if (leaveIfBranchGoto != null) leaveIfBranchGoto.setJumpType(JumpType.GOTO_OUT_OF_IF);
        innerIfStatement.setJumpType(JumpType.GOTO_OUT_OF_IF);
        innerIfStatement.setKnownBlocks(ifBlockLabel, elseBlockLabel);
        ignoreTheseJumps.addAll(ignoreLocally);
        return true;
    }

    public static void identifyNonjumpingConditionals(List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory) {
        boolean success = false;
        Set<Op03SimpleStatement> ignoreTheseJumps = SetFactory.newSet();
        do {
            success = false;
            List<Op03SimpleStatement> forwardIfs = Functional.filter(statements, new IsForwardIf());
            Collections.reverse(forwardIfs);
            for (Op03SimpleStatement forwardIf : forwardIfs) {
                if (considerAsTrivialIf(forwardIf, statements, blockIdentifierFactory, ignoreTheseJumps) ||
                        considerAsSimpleIf(forwardIf, statements, blockIdentifierFactory, ignoreTheseJumps) ||
                        considerAsDexIf(forwardIf, statements, blockIdentifierFactory, ignoreTheseJumps)) {
                    success = true;
                }
            }
        } while (success);
    }


    public static List<Op03SimpleStatement> removeUselessNops(List<Op03SimpleStatement> in) {
        return Functional.filter(in, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return !(in.sources.isEmpty() && in.targets.isEmpty());
            }
        });
    }

    public static List<Op03SimpleStatement> rewriteWith(List<Op03SimpleStatement> in, ExpressionRewriter expressionRewriter) {
        for (Op03SimpleStatement op03SimpleStatement : in) {
            op03SimpleStatement.rewrite(expressionRewriter);
        }
        return in;
    }

    private static class GraphVisitorBlockReachable implements BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>> {

        private final Op03SimpleStatement start;
        private final BlockIdentifier blockIdentifier;
        private final Set<Op03SimpleStatement> found = SetFactory.newSet();

        private GraphVisitorBlockReachable(Op03SimpleStatement start, BlockIdentifier blockIdentifier) {
            this.start = start;
            this.blockIdentifier = blockIdentifier;
        }

        @Override
        public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
            if (arg1 == start || arg1.getBlockIdentifiers().contains(blockIdentifier)) {
                found.add(arg1);
                for (Op03SimpleStatement target : arg1.getTargets()) arg2.enqueue(target);
            }
        }

        public Set<Op03SimpleStatement> run() {
            GraphVisitorDFS<Op03SimpleStatement> reachableInBlock = new GraphVisitorDFS<Op03SimpleStatement>(
                    start,
                    this
            );
            reachableInBlock.process();
            return found;
        }

    }


    private static void combineTryCatchBlocks(final Op03SimpleStatement tryStatement, List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory) {
        Set<Op03SimpleStatement> allStatements = SetFactory.newSet();
        TryStatement innerTryStatement = (TryStatement) tryStatement.getStatement();

        allStatements.addAll(new GraphVisitorBlockReachable(tryStatement, innerTryStatement.getBlockIdentifier()).run());

        // all in block, reachable
        for (Op03SimpleStatement target : tryStatement.getTargets()) {
            if (target.containedStatement instanceof CatchStatement) {
                CatchStatement catchStatement = (CatchStatement) target.containedStatement;
                allStatements.addAll(new GraphVisitorBlockReachable(target, catchStatement.getCatchBlockIdent()).run());
            }
        }

        /* Add something inFRONT of the try statement which is NOT going to be in this block, which can adopt it
         * (This is obviously an unreal artifact)
         */
        Set<BlockIdentifier> tryBlocks = tryStatement.containedInBlocks;
        if (tryBlocks.isEmpty()) return;
        for (Op03SimpleStatement statement : allStatements) {
            statement.containedInBlocks.addAll(tryBlocks);
        }


    }

    // Up to now, try and catch blocks, while related, are treated in isolation.
    // We need to make sure they're logically grouped, so we can see when a block constraint is being violated.
    public static void combineTryCatchBlocks(List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory) {
        List<Op03SimpleStatement> tries = Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement tryStatement : tries) {
            combineTryCatchBlocks(tryStatement, in, blockIdentifierFactory);
        }

    }


    private static void combineTryCatchEnds(Op03SimpleStatement tryStatement, List<Op03SimpleStatement> in) {
        TryStatement innerTryStatement = (TryStatement) tryStatement.getStatement();
        List<Op03SimpleStatement> lastStatements = ListFactory.newList();
        lastStatements.add(getLastContiguousBlockStatement(innerTryStatement.getBlockIdentifier(), in, tryStatement));
        for (int x = 1, len = tryStatement.targets.size(); x < len; ++x) {
            Op03SimpleStatement statementContainer = tryStatement.targets.get(x);
            Statement statement = statementContainer.getStatement();
            if (statement instanceof CatchStatement) {
                lastStatements.add(getLastContiguousBlockStatement(((CatchStatement) statement).getCatchBlockIdent(), in, statementContainer));
            } else if (statement instanceof FinallyStatement) {
                return;
//                lastStatements.add(getLastContiguousBlockStatement(((FinallyStatement) statement).getFinallyBlockIdent(), in, statementContainer));
            } else {
                return;
            }
        }
        if (lastStatements.size() <= 1) return;
        for (Op03SimpleStatement last : lastStatements) {
            if (last == null) return;
            if (last.getStatement().getClass() != GotoStatement.class) {
                return;
            }
        }
        Op03SimpleStatement target = lastStatements.get(0).getTargets().get(0);
        for (Op03SimpleStatement last : lastStatements) {
            if (last.getTargets().get(0) != target) return;
        }
        // Insert a fake target after the final one.
        Op03SimpleStatement finalStatement = lastStatements.get(lastStatements.size() - 1);
        int beforeTgt = in.indexOf(finalStatement);
        Op03SimpleStatement proxy = new Op03SimpleStatement(tryStatement.getBlockIdentifiers(), new GotoStatement(), finalStatement.getIndex().justAfter());
        in.add(beforeTgt + 1, proxy);
        proxy.addTarget(target);
        target.addSource(proxy);

        for (Op03SimpleStatement last : lastStatements) {
            GotoStatement gotoStatement = (GotoStatement) last.containedStatement;
            gotoStatement.setJumpType(JumpType.END_BLOCK);
            last.replaceTarget(target, proxy);
            target.removeSource(last);
            proxy.addSource(last);
        }

        int x = 1;

    }

    private static void rewriteTryBackJump(Op03SimpleStatement stm) {
        InstrIndex idx = stm.getIndex();
        TryStatement tryStatement = (TryStatement) stm.getStatement();
        Op03SimpleStatement firstbody = stm.getTargets().get(0);
        BlockIdentifier blockIdentifier = tryStatement.getBlockIdentifier();
        Iterator<Op03SimpleStatement> sourceIter = stm.sources.iterator();
        while (sourceIter.hasNext()) {
            Op03SimpleStatement source = sourceIter.next();
            if (idx.isBackJumpFrom(source)) {
                if (source.getBlockIdentifiers().contains(blockIdentifier)) {
                    source.replaceTarget(stm, firstbody);
                    firstbody.addSource(source);
                    sourceIter.remove(); // remove source inline.
                }
            }
        }
    }

    /*
     * If there's a backjump INSIDE a try block which points to the beginning of the try block, move it to the next
     * instruction.
     */
    public static void rewriteTryBackJumps(List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tries = Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement trystm : tries) {
            rewriteTryBackJump(trystm);
        }
    }

    public static void combineTryCatchEnds(List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tries = Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement tryStatement : tries) {
            combineTryCatchEnds(tryStatement, in);
        }
    }

    /*
     * The Op4 -> Structured op4 transform requires blocks to have a member, in order to trigger the parent being claimed.
     * We may need to add synthetic block entries.
     *
     * Because there is an assumption that all statements are in 'statements'
     * todo : remove this assumption!
     * we need to link it to the end.
     */
    private static Op03SimpleStatement insertBlockPadding(String comment, Op03SimpleStatement insertAfter, Op03SimpleStatement insertBefore, BlockIdentifier blockIdentifier, List<Op03SimpleStatement> statements) {
        Op03SimpleStatement between = new Op03SimpleStatement(insertAfter.getBlockIdentifiers(), new CommentStatement(comment), insertAfter.getIndex().justAfter());
        insertAfter.replaceTarget(insertBefore, between);
        insertBefore.replaceSource(insertAfter, between);
        between.addSource(insertAfter);
        between.addTarget(insertBefore);
        between.getBlockIdentifiers().add(blockIdentifier);
        statements.add(between);
        return between;
    }

    /*
     * Could be refactored out as uniquelyReachableFrom....
     */
    private static void identifyCatchBlock(Op03SimpleStatement start, BlockIdentifier blockIdentifier, List<Op03SimpleStatement> statements) {
        Set<Op03SimpleStatement> knownMembers = SetFactory.newSet();
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        seen.add(start);
        knownMembers.add(start);

        /*
         * Because a conditional will cause us to hit a parent which isn't in the block
         * if (a) goto x
         * ..
         * goto z
         * x:
         * ...
         * z
         *
         * (at z we'll see the parent from x before we've marked it)
         * , we have to make sure that we've exhausted pending possibilities before
         * we decide we're reachable from something outside the catch block.
         * (this is different to tentative marking, as there we're examining nodes
         * which are reachable from something we're not sure is actually in the block
         */
        LinkedList<Op03SimpleStatement> pendingPossibilities = ListFactory.newLinkedList();
        if (start.targets.size() != 1) {
            throw new ConfusedCFRException("Catch statement with multiple targets");
        }
        for (Op03SimpleStatement target : start.targets) {
            pendingPossibilities.add(target);
            seen.add(target);
        }

        Map<Op03SimpleStatement, Set<Op03SimpleStatement>> allows = MapFactory.newLazyMap(new UnaryFunction<Op03SimpleStatement, Set<Op03SimpleStatement>>() {
            @Override
            public Set<Op03SimpleStatement> invoke(Op03SimpleStatement ignore) {
                return SetFactory.newSet();
            }
        });
        int sinceDefinite = 0;
        while (!pendingPossibilities.isEmpty() && sinceDefinite <= pendingPossibilities.size()) {
            Op03SimpleStatement maybe = pendingPossibilities.removeFirst();
            boolean definite = true;
            for (Op03SimpleStatement source : maybe.sources) {
                if (!knownMembers.contains(source)) {
                    /* If it's a backjump, we'll allow it.
                     * We won't tag the source as good, which means that we may have gaps
                     * if it turns out this was invalid.
                     */
                    if (!source.getIndex().isBackJumpTo(maybe)) {
                        definite = false;
                        allows.get(source).add(maybe);
                    }
                }
            }
            if (definite) {
                sinceDefinite = 0;
                // All of this guys sources are known
                knownMembers.add(maybe);
                Set<Op03SimpleStatement> allowedBy = allows.get(maybe);
                pendingPossibilities.addAll(allowedBy);
                // They'll get re-added if they're still blocked.
                allowedBy.clear();
                /* OrderCheat :
                 * only add backTargets which are to after the catch block started.
                 */
                for (Op03SimpleStatement target : maybe.targets) {
                    // Don't need to check knownMembers, always included in seen.
                    if (!seen.contains(target)) {
                        seen.add(target);
                        if (target.getIndex().isBackJumpTo(start)) {
                            pendingPossibilities.add(target);
                        }
                    }
                }
            } else {
                /*
                 * Can't reach this one (or certainly, can't reach it given what we know yet)
                 */
                sinceDefinite++;
                pendingPossibilities.add(maybe);
            }
        }
        /*
         * knownMembers now defines the graph uniquely reachable from start.
         * TODO :
         * Now we have to check how well it lines up with the linear code assumption.
         */
        knownMembers.remove(start);
        if (knownMembers.isEmpty()) {
            List<Op03SimpleStatement> targets = start.getTargets();
            // actually already verified above, but I'm being paranoid.
            if (targets.size() != 1) throw new ConfusedCFRException("Synthetic catch block has multiple targets");
            knownMembers.add(insertBlockPadding("empty catch block", start, targets.get(0), blockIdentifier, statements));
        }
        /*
         * But now we have to remove (boo) non contiguous ones.
         * ORDERCHEAT.
         *
         * This is because otherwise we'll jump out, and back in to a block.
         *
         * Sort knownMembers
         */
        List<Op03SimpleStatement> knownMemberList = ListFactory.newList(knownMembers);
        Collections.sort(knownMemberList, new CompareByIndex());

        List<Op03SimpleStatement> truncatedKnownMembers = ListFactory.newList();
        int x = statements.indexOf(knownMemberList.get(0));
        List<Op03SimpleStatement> flushNops = ListFactory.newList();
        for (int l = statements.size(); x < l; ++x) {
            Op03SimpleStatement statement = statements.get(x);
            if (statement.isNop()) {
                flushNops.add(statement);
                continue;
            }
            if (!knownMembers.contains(statement)) break;
            truncatedKnownMembers.add(statement);
            if (!flushNops.isEmpty()) {
                truncatedKnownMembers.addAll(flushNops);
                flushNops.clear();
            }
        }

        for (Op03SimpleStatement inBlock : truncatedKnownMembers) {
            inBlock.containedInBlocks.add(blockIdentifier);
        }
        /*
         * Find the (there should only be one) descendant of start.  It /SHOULD/ be the first sorted member of
         * known members, otherwise we have a problem.  Mark that as start of block.
         */
        Op03SimpleStatement first = start.getTargets().get(0);
        first.markFirstStatementInBlock(blockIdentifier);
    }

    /* Basic principle with catch blocks - we mark all statements from the start
     * of a catch block, UNTIL they can be reached by something that isn't marked.
     *
     * Complexity comes from allowing back jumps inside a catch block.  If there's a BACK
     * JUMP
     * TODO : OrderCheat
     * which is not from a catchblock statement, we have to mark current location as
     * "last known guaranteed".  We then proceed, tentatively marking.
     *
     * As soon as we hit something which /can't/ be in the catch block, we can
     * unwind all tentatives which assume that it was.
     */
    public static void identifyCatchBlocks(List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory) {
        List<Op03SimpleStatement> catchStarts = Functional.filter(in, new TypeFilter<CatchStatement>(CatchStatement.class));
        for (Op03SimpleStatement catchStart : catchStarts) {
            CatchStatement catchStatement = (CatchStatement) catchStart.containedStatement;
            if (catchStatement.getCatchBlockIdent() == null) {
                BlockIdentifier blockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.CATCHBLOCK);
                catchStatement.setCatchBlockIdent(blockIdentifier);
                identifyCatchBlock(catchStart, blockIdentifier, in);
            }
        }
    }

    /*
     * Find the last statement in the block, assuming that this statement is the one BEFORE, linearly.
     */
    private static Op03SimpleStatement getLastContiguousBlockStatement(BlockIdentifier blockIdentifier, List<Op03SimpleStatement> in, Op03SimpleStatement preBlock) {
        if (preBlock.targets.isEmpty()) return null;
        Op03SimpleStatement currentStatement = preBlock.targets.get(0);
        int x = in.indexOf(currentStatement);

        if (!currentStatement.getBlockIdentifiers().contains(blockIdentifier)) return null;

        Op03SimpleStatement last = currentStatement;
        while (currentStatement.getBlockIdentifiers().contains(blockIdentifier)) {
            ++x;
            if (x >= in.size()) {
                break;
            }
            last = currentStatement;
            currentStatement = in.get(x);
        }
        return last;
    }

    /*
     * This is a terrible order cheat.
     */
    private static void extendTryBlock(Op03SimpleStatement tryStatement, List<Op03SimpleStatement> in, DCCommonState dcCommonState) {
        TryStatement tryStatementInner = (TryStatement) tryStatement.getStatement();
        BlockIdentifier tryBlockIdent = tryStatementInner.getBlockIdentifier();

        Op03SimpleStatement currentStatement = tryStatement.targets.get(0);
        int x = in.indexOf(currentStatement);

        while (currentStatement.getBlockIdentifiers().contains(tryBlockIdent)) {
            ++x;
            if (x >= in.size()) {
                return;
            }
            currentStatement = in.get(x);
        }

        /*
         * Get the types of all caught expressions.  Anything we can't understand resolves to runtime expression
         * This allows us to extend exception blocks if they're catching checked exceptions, and we can tell that no
         * checked exceptions could be thrown.
         */
        Set<JavaRefTypeInstance> caught = SetFactory.newSet();
        List<Op03SimpleStatement> targets = tryStatement.targets;
        for (int i = 1, len = targets.size(); i < len; ++i) {
            Statement statement = targets.get(i).getStatement();
            if (!(statement instanceof CatchStatement)) continue;
            CatchStatement catchStatement = (CatchStatement) statement;
            List<ExceptionGroup.Entry> exceptions = catchStatement.getExceptions();
            for (ExceptionGroup.Entry entry : exceptions) {
                caught.add(entry.getCatchType());
            }
        }

        ExceptionCheck exceptionCheck = new ExceptionCheck(dcCommonState, caught);

        mainloop:
        while (!currentStatement.getStatement().canThrow(exceptionCheck)) {
            Set<BlockIdentifier> validBlocks = SetFactory.newSet();
            validBlocks.add(tryBlockIdent);
            for (int i = 1, len = tryStatement.targets.size(); i < len; ++i) {
                Op03SimpleStatement tgt = tryStatement.targets.get(i);
                Statement tgtStatement = tgt.getStatement();
                if (tgtStatement instanceof CatchStatement) {
                    validBlocks.add(((CatchStatement) tgtStatement).getCatchBlockIdent());
                } else if (tgtStatement instanceof FinallyStatement) {
                    validBlocks.add(((FinallyStatement) tgtStatement).getFinallyBlockIdent());
                } else {
                    return;
                }
            }

            boolean foundSource = false;
            for (Op03SimpleStatement source : currentStatement.sources) {
                if (!SetUtil.hasIntersection(validBlocks, source.getBlockIdentifiers())) return;
                if (source.getBlockIdentifiers().contains(tryBlockIdent)) foundSource = true;
            }

            if (!foundSource) return;
            /*
             * If this statement is a return statement, in the same blocks as JUST THE try (i.e. it hasn't fallen
             * into a catch etc), we can assume it belonged in the try.
             */
            currentStatement.getBlockIdentifiers().add(tryBlockIdent);

            x++;
            if (x >= in.size()) break;
            Op03SimpleStatement nextStatement = in.get(x);
            if (!currentStatement.getTargets().contains(nextStatement)) {
                // Then ALL of nextStatements sources must ALREADY be in the block.
                for (Op03SimpleStatement source : nextStatement.getSources()) {
                    if (!source.getBlockIdentifiers().contains(tryBlockIdent)) break mainloop;
                }
            }
            currentStatement = nextStatement;
        }
    }

    public static void extendTryBlocks(DCCommonState dcCommonState, List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tries = Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement tryStatement : tries) {
            extendTryBlock(tryStatement, in, dcCommonState);
        }

    }

    public static void identifyFinally(Options options, Method method, List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory) {
        if (!options.getOption(OptionsImpl.DECODE_FINALLY)) return;
        /* Get all the try statements, get their catches.  For all the EXIT points to the catches, try to identify
         * a common block of code (either before a throw, return or goto.)
         * Be careful, if a finally block contains a throw, this will mess up...
         */
        final Set<Op03SimpleStatement> analysedTries = SetFactory.newSet();
        boolean continueLoop;
        do {
            List<Op03SimpleStatement> tryStarts = Functional.filter(in, new Predicate<Op03SimpleStatement>() {
                @Override
                public boolean test(Op03SimpleStatement in) {
                    if (in.getStatement() instanceof TryStatement &&
                            !analysedTries.contains(in)) return true;
                    return false;
                }
            });
            for (Op03SimpleStatement tryS : tryStarts) {
                FinalAnalyzer.identifyFinally(method, tryS, in, blockIdentifierFactory, analysedTries);
            }
            /*
             * We may need to reloop, if analysis has created new tries inside finally handlers. (!).
             */
            continueLoop = (!tryStarts.isEmpty());
        } while (continueLoop);
    }

    public static List<Op03SimpleStatement> removeRedundantTries(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> tryStarts = Functional.filter(statements, new TypeFilter<TryStatement>(TryStatement.class));
        /*
         * If the try doesn't point at a member of the try, it's been made redundant.
         * Verify that no other references to its' block exist, and remove it.
         * (Verification should be unneccesary)
         */
        boolean effect = false;
        Collections.reverse(tryStarts);
        LinkedList<Op03SimpleStatement> starts = ListFactory.newLinkedList();
        starts.addAll(tryStarts);
        while (!starts.isEmpty()) {
            Op03SimpleStatement trys = starts.removeFirst();
            Statement stm = trys.getStatement();
            if (!(stm instanceof TryStatement)) continue;
            TryStatement tryStatement = (TryStatement) stm;
            BlockIdentifier tryBlock = tryStatement.getBlockIdentifier();
            if (trys.targets.isEmpty() || !trys.targets.get(0).getBlockIdentifiers().contains(tryBlock)) {
                // Remove this try.
                Op03SimpleStatement codeTarget = trys.targets.get(0);

                for (Op03SimpleStatement target : trys.targets) {
                    target.removeSource(trys);
                }
                trys.targets.clear();
                for (Op03SimpleStatement source : trys.sources) {
                    source.replaceTarget(trys, codeTarget);
                    codeTarget.addSource(source);
                }
                trys.sources.clear();
                effect = true;
            }
        }

        if (effect) {
            statements = Op03SimpleStatement.removeUnreachableCode(statements, false);
            statements = renumber(statements);
        }

        return statements;
    }

    private static boolean verifyLinearBlock(Op03SimpleStatement current, BlockIdentifier block, int num) {
        while (num >= 0) {
            if (num > 0) {
                if (current.getStatement() instanceof Nop && current.targets.size() == 0) {
                    break;
                }
                if (current.targets.size() != 1) {
                    return false;
                }
                if (!current.containedInBlocks.contains(block)) {
                    return false;
                }
                current = current.targets.get(0);
            } else {
                if (!current.containedInBlocks.contains(block)) {
                    return false;
                }
            }
            num--;
        }
        // None of current's targets should be contained in block.
        for (Op03SimpleStatement target : current.targets) {
            if (target.containedInBlocks.contains(block)) {
                return false;
            }
        }
        return true;
    }

    /*
    * Because of the way we generate code, this will look like
    *
    * x = stack
    * monitorexit (a)
    * throw x
    */
    private static boolean removeSynchronizedCatchBlock(Op03SimpleStatement start, List<Op03SimpleStatement> statements) {

        BlockIdentifier block = start.firstStatementInThisBlock;

        if (start.sources.size() != 1) return false;
        Op03SimpleStatement catchStatementContainer = start.sources.get(0);
        // Again, the catch statement should have only one source.
        if (catchStatementContainer.sources.size() != 1) return false;
        Statement catchOrFinally = catchStatementContainer.containedStatement;
        boolean isFinally = false;
        if (catchOrFinally instanceof CatchStatement) {
            CatchStatement catchStatement = (CatchStatement) catchStatementContainer.containedStatement;
            List<ExceptionGroup.Entry> exceptions = catchStatement.getExceptions();
            if (exceptions.size() != 1) return false;
            ExceptionGroup.Entry exception = exceptions.get(0);
            // Exception is *.
            if (!exception.isJustThrowable()) return false;
        } else if (catchOrFinally instanceof FinallyStatement) {
            isFinally = true;
        } else {
            return false;
        }

        // We expect the next 2 and NO more to be in this catch block.
        if (!verifyLinearBlock(start, block, 2)) {
            return false;
        }

        Op03SimpleStatement variableAss;
        Op03SimpleStatement monitorExit;
        Op03SimpleStatement rethrow;

        if (isFinally) {
            monitorExit = start;
            variableAss = null;
            rethrow = null;
        } else {
            variableAss = start;
            monitorExit = start.targets.get(0);
            rethrow = monitorExit.targets.get(0);
        }

        WildcardMatch wildcardMatch = new WildcardMatch();

        if (!isFinally) {
            if (!wildcardMatch.match(
                    new AssignmentSimple(wildcardMatch.getLValueWildCard("var"), wildcardMatch.getExpressionWildCard("e")),
                    variableAss.containedStatement)) {
                return false;
            }
        }

        if (!wildcardMatch.match(
                new MonitorExitStatement(wildcardMatch.getExpressionWildCard("lock")),
                monitorExit.containedStatement)) {
            return false;
        }

        if (!isFinally) {
            if (!wildcardMatch.match(
                    new ThrowStatement(new LValueExpression(wildcardMatch.getLValueWildCard("var"))),
                    rethrow.containedStatement)) return false;
        }

        Op03SimpleStatement tryStatementContainer = catchStatementContainer.sources.get(0);

        if (isFinally) {
            MonitorExitStatement monitorExitStatement = (MonitorExitStatement) monitorExit.getStatement();
            TryStatement tryStatement = (TryStatement) tryStatementContainer.getStatement();
            tryStatement.addExitMutex(monitorExitStatement.getMonitor());
        }

        /* This is an artificial catch block - probably.  Remove it, and if we can, remove the associated try
         * statement.
         * (This only makes sense if we eventually replace the MONITOR(ENTER|EXIT) pair with a synchronized
         * block).
         */
        tryStatementContainer.removeTarget(catchStatementContainer);
        catchStatementContainer.removeSource(tryStatementContainer);
        catchStatementContainer.nopOut();
        if (!isFinally) {
            variableAss.nopOut();
        }
        monitorExit.nopOut();
        if (!isFinally) {
            for (Op03SimpleStatement target : rethrow.targets) {
                target.removeSource(rethrow);
                rethrow.removeTarget(target);
            }
            rethrow.nopOut();
        }


        /*
         * Can we remove the try too?
         */
        if (tryStatementContainer.targets.size() == 1 && !isFinally) {
            TryStatement tryStatement = (TryStatement) tryStatementContainer.containedStatement;
            BlockIdentifier tryBlock = tryStatement.getBlockIdentifier();
            tryStatementContainer.nopOut();
            /* And we have to remove this block from all statements.
             * TODO: This is inefficient - we could just have a concept of 'dead' blocks.
             */
            for (Op03SimpleStatement statement : statements) {
                statement.containedInBlocks.remove(tryBlock);
            }
        }
        return true;
    }

    public static void commentMonitors(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> monitors = Functional.filter(statements, new TypeFilter<MonitorStatement>(MonitorStatement.class));
        if (monitors.isEmpty()) return;
        for (Op03SimpleStatement monitor : monitors) {
            monitor.replaceStatement(new CommentStatement(monitor.getStatement()));
        }
        /*
         * Any jumps to one of these statements which jump into the MIDDLE of a block is a problem.  If we can jump to
         * after this statement and NOT be in the middle of a block, prefer that.
         * [This is very much a heuristic required by dex2jar]
         */
        for (Op03SimpleStatement monitor : monitors) {
            /*
             * Is monitor (as was) the last statement in a block.
             */
            Op03SimpleStatement target = monitor.getTargets().get(0);
            Set<BlockIdentifier> monitorLast = SetFactory.newSet(monitor.getBlockIdentifiers());
            monitorLast.removeAll(target.getBlockIdentifiers());
            if (monitorLast.isEmpty()) continue;
            for (Op03SimpleStatement source : ListFactory.newList(monitor.sources)) {
                Set<BlockIdentifier> sourceBlocks = source.getBlockIdentifiers();
                if (!sourceBlocks.containsAll(monitorLast)) {
                    /*
                     * Let's redirect source to point to AFTER monitor statement.
                     */
                    source.replaceTarget(monitor, target);
                    monitor.removeSource(source);
                    target.addSource(source);
                }
            }
        }
    }

    /*
     * TODO : Defeatable.
     * Every time we write
     *
     * synchronized(x) {
     *  y
     * }
     *
     * we emit
     *
     * try {
     *   MONITORENTER(x)
     *   y
     *   MONITOREXIT(x)
     * } catch (Throwable t) {
     *   MONITOREXIT(x)
     *   throw t;
     * }
     *
     * Remove the catch block and try statement.
     */
    public static void removeSynchronizedCatchBlocks(Options options, List<Op03SimpleStatement> in) {
        if (!options.getOption(OptionsImpl.TIDY_MONITORS)) return;
        // find all the block statements which are the first statement in a CATCHBLOCK.
        List<Op03SimpleStatement> catchStarts = Functional.filter(in, new FindBlockStarts(BlockType.CATCHBLOCK));
        if (catchStarts.isEmpty()) return;
        boolean effect = false;
        for (Op03SimpleStatement catchStart : catchStarts) {
            effect = removeSynchronizedCatchBlock(catchStart, in) || effect;
        }
        if (effect) {
            removePointlessJumps(in);
        }
    }

    private final static class FindBlockStarts implements Predicate<Op03SimpleStatement> {
        private final BlockType blockType;

        public FindBlockStarts(BlockType blockType) {
            this.blockType = blockType;
        }

        @Override
        public boolean test(Op03SimpleStatement in) {
            BlockIdentifier blockIdentifier = in.firstStatementInThisBlock;
            if (blockIdentifier == null) return false;
            return (blockIdentifier.getBlockType() == blockType);
        }
    }

    public static void replaceRawSwitch(Op03SimpleStatement swatch, List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory) {
        List<Op03SimpleStatement> targets = swatch.targets;
        RawSwitchStatement switchStatement = (RawSwitchStatement) swatch.containedStatement;
        DecodedSwitch switchData = switchStatement.getSwitchData();
        BlockIdentifier switchBlockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.SWITCH);
        /*
         * If all of the targets /INCLUDING DEFAULT/ point at the same place, replace the switch with a goto, and
         * leave it at that.
         */
        Op03SimpleStatement oneTarget = targets.get(0);
        boolean mismatch = false;
        for (int x = 1; x < targets.size(); ++x) {
            Op03SimpleStatement target = targets.get(x);
            if (target != oneTarget) {
                mismatch = true;
                break;
            }
        }
        if (!mismatch) {
            /*
             * TODO : May have to clear out sources / targets?
             */
            swatch.replaceStatement(new GotoStatement());
            return;
        }

        // For each of the switch targets, add a 'case' statement
        // We can add them at the end, as long as we've got a post hoc sort.

        // What happens if there's no default statement?  Not sure java permits?
        List<DecodedSwitchEntry> entries = switchData.getJumpTargets();
        InferredJavaType caseType = switchStatement.getSwitchOn().getInferredJavaType();
        Map<InstrIndex, Op03SimpleStatement> firstPrev = MapFactory.newMap();
        for (int x = 0; x < targets.size(); ++x) {
            Op03SimpleStatement target = targets.get(x);
            InstrIndex tindex = target.getIndex();
            if (firstPrev.containsKey(tindex)) { // Get previous case statement if already exists, so we stack them.
                target = firstPrev.get(tindex);
            }
            List<Expression> expression = ListFactory.newList();
            // 0 is default, no values.
            if (x != 0) {
                // Otherwise we know that our branches are in the same order as our targets.
                List<Integer> vals = entries.get(x - 1).getValue();
                for (int val : vals) {
                    expression.add(new Literal(TypedLiteral.getInt(val)));
                }
            }
            Set<BlockIdentifier> blocks = SetFactory.newSet(target.getBlockIdentifiers());
            blocks.add(switchBlockIdentifier);
            BlockIdentifier caseIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.CASE);
            Op03SimpleStatement caseStatement = new Op03SimpleStatement(blocks, new CaseStatement(expression, caseType, switchBlockIdentifier, caseIdentifier), target.getIndex().justBefore());
            // Link casestatement in infront of target - all sources of target should point to casestatement instead, and
            // there should be one link going from caseStatement to target. (it's unambiguous).
            Iterator<Op03SimpleStatement> iterator = target.sources.iterator();
            while (iterator.hasNext()) {
                Op03SimpleStatement source = iterator.next();
                if (swatch.getIndex().isBackJumpTo(source)) {
                    continue;
                }
                if (source.getIndex().isBackJumpTo(target)) {
                    continue;
                }
                source.replaceTarget(target, caseStatement);
                caseStatement.addSource(source);
                iterator.remove();
            }
            target.sources.add(caseStatement);
            caseStatement.addTarget(target);
            in.add(caseStatement);
            firstPrev.put(tindex, caseStatement);
        }

        renumberInPlace(in);
        /*
         * Now, we have one final possible issue - what if a case block is reachable from a LATER block?
         * i.e.
         *
         * case 1:
         *   goto b
         * case 2:
         *   goto a
         * ...
         * ...
         * a:
         *
         * return
         * b:
         * ...
         * ...
         * goto a
         */
        buildSwitchCases(swatch, targets, switchBlockIdentifier, in);

        swatch.replaceStatement(switchStatement.getSwitchStatement(switchBlockIdentifier));
        /*
         * And (as it doesn't matter) reorder swatch's targets, for minimal confusion
         */
        Collections.sort(swatch.targets, new CompareByIndex());
    }

    public static void rebuildSwitches(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> switchStatements = Functional.filter(statements, new TypeFilter<SwitchStatement>(SwitchStatement.class));
        /*
         * Get the block identifiers for all switch statements and their cases.
         */
        for (Op03SimpleStatement switchStatement : switchStatements) {
            SwitchStatement switchStatementInr = (SwitchStatement) switchStatement.getStatement();
            Set<BlockIdentifier> allBlocks = SetFactory.newSet();
            allBlocks.add(switchStatementInr.getSwitchBlock());
            for (Op03SimpleStatement target : switchStatement.targets) {
                Statement stmTgt = target.getStatement();
                if (stmTgt instanceof CaseStatement) {
                    allBlocks.add(((CaseStatement) stmTgt).getCaseBlock());
                }
            }
            for (Op03SimpleStatement stm : statements) {
                stm.getBlockIdentifiers().removeAll(allBlocks);
            }
            buildSwitchCases(switchStatement, switchStatement.getTargets(), switchStatementInr.getSwitchBlock(), statements);
        }
        for (Op03SimpleStatement switchStatement : switchStatements) {
            // removePointlessSwitchDefault(switchStatement);
            examineSwitchContiguity(switchStatement, statements);
        }

    }

    private static void buildSwitchCases(Op03SimpleStatement swatch, List<Op03SimpleStatement> targets, BlockIdentifier switchBlockIdentifier, List<Op03SimpleStatement> in) {
        Set<BlockIdentifier> caseIdentifiers = SetFactory.newSet();
        /*
         * For each of the case statements - find which is reachable from the others WITHOUT going through
         * the switch again.  Then we might have to move a whole block... (!).
         *
         * If this is the case, we then figure out which order the switch blocks depend on each other, and perform a code
         * reordering walk through the case statements in new order (and re-write the order of the targets.
         */
        Set<Op03SimpleStatement> caseTargets = SetFactory.newSet(targets);
        /*
         * For the START of each block, find if it's reachable from the start of the others, without going through
         * switch.
         * // /Users/lee/Downloads/samples/android/support/v4/app/FragmentActivity.class
         */
        Map<Op03SimpleStatement, InstrIndex> lastStatementBefore = MapFactory.newMap();
        for (Op03SimpleStatement target : targets) {
            CaseStatement caseStatement = (CaseStatement) target.getStatement();
            BlockIdentifier caseBlock = caseStatement.getCaseBlock();

            NodeReachable nodeReachable = new NodeReachable(caseTargets, target, swatch);
            GraphVisitor<Op03SimpleStatement> gv = new GraphVisitorDFS<Op03SimpleStatement>(target, nodeReachable);
            gv.process();

            List<Op03SimpleStatement> backReachable = Functional.filter(nodeReachable.reaches, new IsForwardJumpTo(target.getIndex()));
            if (backReachable.isEmpty()) continue;

            if (backReachable.size() != 1) {
//                throw new IllegalStateException("Can't handle case statement with multiple reachable back edges to other cases (yet)");
                continue;
            }

            Op03SimpleStatement backTarget = backReachable.get(0);
            /*
             * If this block is contiguous AND fully contains the found nodes, AND has no other sources, we can re-label
             * all of the entries, and place it just before backTarget.
             *
             * Need to cache the last entry, as we might move multiple blocks.
             *
             */
            boolean contiguous = blockIsContiguous(in, target, nodeReachable.inBlock);
            if (target.getSources().size() != 1) {
                /*
                 * Ok, we can't move this above.  But we need to make sure, (if it's the last block) that we've explicitly
                 * marked the contents as being in this switch, to stop movement.
                 */
                if (contiguous) {
                    for (Op03SimpleStatement reachable : nodeReachable.inBlock) {
                        reachable.markBlock(switchBlockIdentifier);
                        // TODO : FIXME : Expensive - can we assume we won't get asked to mark
                        // members of other cases?
                        if (!caseTargets.contains(reachable)) {
                            if (!SetUtil.hasIntersection(reachable.getBlockIdentifiers(), caseIdentifiers)) {
                                reachable.markBlock(caseBlock);
                            }
                        }
                    }
                }
                continue;
            }
            if (!contiguous) {
                // Can't handle this.
                continue;
            }
            // Yay.  Move (in order) all these statements to before backTarget.
            // Well, don't really move them.  Relabel them.
            InstrIndex prev = lastStatementBefore.get(backTarget);
            if (prev == null) {
                prev = backTarget.getIndex().justBefore();
            }
            int idx = in.indexOf(target) + nodeReachable.inBlock.size() - 1;
            for (int i = 0, len = nodeReachable.inBlock.size(); i < len; ++i, --idx) {
                in.get(idx).setIndex(prev);
                prev = prev.justBefore();
            }
            lastStatementBefore.put(backTarget, prev);
//            throw new IllegalStateException("Backjump fallthrough");
        }
    }

    private static boolean blockIsContiguous(List<Op03SimpleStatement> in, Op03SimpleStatement start, Set<Op03SimpleStatement> blockContent) {
        int idx = in.indexOf(start);
        int len = blockContent.size();
        if (idx + blockContent.size() > in.size()) return false;
        for (int found = 1; found < len; ++found, ++idx) {
            Op03SimpleStatement next = in.get(idx);
            if (!blockContent.contains(next)) {
                return false;
            }
        }
        return true;
    }

    /* This WILL go too far, as we have no way of knowing when the common code ends....
     *
     */
    private static class NodeReachable implements BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>> {

        private final Set<Op03SimpleStatement> otherCases;
        private final Op03SimpleStatement switchStatement;

        private final Op03SimpleStatement start;
        private final List<Op03SimpleStatement> reaches = ListFactory.newList();
        private final Set<Op03SimpleStatement> inBlock = SetFactory.newSet();

        private NodeReachable(Set<Op03SimpleStatement> otherCases, Op03SimpleStatement start, Op03SimpleStatement switchStatement) {
            this.otherCases = otherCases;
            this.switchStatement = switchStatement;
            this.start = start;
        }

        @Override
        public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
            if (arg1 == switchStatement) {
                return;
            }
            if (arg1.getIndex().isBackJumpFrom(start)) {
                // If it's a backjump from the switch statement as well, ignore.  Otherwise we have to process.
                if (arg1.getIndex().isBackJumpFrom(switchStatement)) return;
            }
            if (arg1 != start && otherCases.contains(arg1)) {
                reaches.add(arg1);
                return;
            }
            inBlock.add(arg1);
            arg2.enqueue(arg1.getTargets());
        }
    }


    private static boolean examineSwitchContiguity(Op03SimpleStatement switchStatement, List<Op03SimpleStatement> statements) {
        Set<Op03SimpleStatement> forwardTargets = SetFactory.newSet();

        // Create a copy of the targets.  We're going to have to copy because we want to sort.
        List<Op03SimpleStatement> targets = ListFactory.newList(switchStatement.targets);
        Collections.sort(targets, new CompareByIndex());

        int idxFirstCase = statements.indexOf(targets.get(0));

        if (idxFirstCase != statements.indexOf(switchStatement) + 1) {
            throw new ConfusedCFRException("First case is not immediately after switch.");
        }

        BlockIdentifier switchBlock = ((SwitchStatement) switchStatement.containedStatement).getSwitchBlock();
        int indexLastInLastBlock = 0;
        // Process all but the last target.  (handle that below, as we may treat it as outside the case block
        // depending on forward targets.
        for (int x = 0; x < targets.size() - 1; ++x) {
            Op03SimpleStatement thisCase = targets.get(x);
            Op03SimpleStatement nextCase = targets.get(x + 1);
            int indexThisCase = statements.indexOf(thisCase);
            int indexNextCase = statements.indexOf(nextCase);
            InstrIndex nextCaseIndex = nextCase.getIndex();

            Statement maybeCaseStatement = thisCase.containedStatement;
            if (!(maybeCaseStatement instanceof CaseStatement)) continue;
            CaseStatement caseStatement = (CaseStatement) maybeCaseStatement;
            BlockIdentifier caseBlock = caseStatement.getCaseBlock();

            int indexLastInThis = getFarthestReachableInRange(statements, indexThisCase, indexNextCase);
            if (indexLastInThis != indexNextCase - 1) {
                // Oh dear.  This is going to need some moving around.
//                throw new ConfusedCFRException("Case statement doesn't cover expected range.");
            }
            indexLastInLastBlock = indexLastInThis;
            for (int y = indexThisCase + 1; y <= indexLastInThis; ++y) {
                Op03SimpleStatement statement = statements.get(y);
                statement.markBlock(caseBlock);
                statement.markBlock(switchBlock);
                if (statement.getJumpType().isUnknown()) {
                    for (Op03SimpleStatement innerTarget : statement.targets) {
                        innerTarget = followNopGoto(innerTarget, false, false);
                        if (nextCaseIndex.isBackJumpFrom(innerTarget)) {
                            forwardTargets.add(innerTarget);
                        }
                    }
                }
            }
        }
        // Either we have zero forwardTargets, in which case we can take the last statement and pull it out,
        // or we have some forward targets.
        // If so, we assume (!!) that's the end, and verify reachability from the start of the last case.
        Op03SimpleStatement lastCase = targets.get(targets.size() - 1);
        int indexLastCase = statements.indexOf(lastCase);
        int breakTarget = -1;
        BlockIdentifier caseBlock = null;
        int indexLastInThis = 0;
        if (!forwardTargets.isEmpty()) {
            List<Op03SimpleStatement> lstFwdTargets = ListFactory.newList(forwardTargets);
            Collections.sort(lstFwdTargets, new CompareByIndex());
            Op03SimpleStatement afterCaseGuess = lstFwdTargets.get(0);
            int indexAfterCase = statements.indexOf(afterCaseGuess);

            CaseStatement caseStatement = (CaseStatement) lastCase.containedStatement;
            caseBlock = caseStatement.getCaseBlock();

            try {
                indexLastInThis = getFarthestReachableInRange(statements, indexLastCase, indexAfterCase);
            } catch (CannotPerformDecode e) {
                forwardTargets.clear();
            }
            if (indexLastInThis != indexAfterCase - 1) {
                // throw new ConfusedCFRException("Final statement in case doesn't meet smallest exit.");
                forwardTargets.clear();
            }
        }
        if (forwardTargets.isEmpty()) {
            for (int y = idxFirstCase; y <= indexLastInLastBlock; ++y) {
                Op03SimpleStatement statement = statements.get(y);
                statement.markBlock(switchBlock);
            }
            if (indexLastCase != indexLastInLastBlock + 1) {
                throw new ConfusedCFRException("Extractable last case doesn't follow previous");
            }
            lastCase.markBlock(switchBlock);
            breakTarget = indexLastCase + 1;
        } else {
            for (int y = indexLastCase + 1; y <= indexLastInThis; ++y) {
                Op03SimpleStatement statement = statements.get(y);
                statement.markBlock(caseBlock);
            }
            for (int y = idxFirstCase; y <= indexLastInThis; ++y) {
                Op03SimpleStatement statement = statements.get(y);
                statement.markBlock(switchBlock);
            }
            breakTarget = indexLastInThis + 1;
        }

        /* Given the assumption that the statement after the switch block is the break target, can we rewrite any
         * of the exits from the switch statement to be breaks?
         */
        Op03SimpleStatement breakStatementTarget = statements.get(breakTarget);
        breakStatementTarget.markPostBlock(switchBlock);
        for (Op03SimpleStatement breakSource : breakStatementTarget.sources) {
            if (breakSource.getBlockIdentifiers().contains(switchBlock)) {
                if (breakSource.getJumpType().isUnknown()) {
                    ((JumpingStatement) breakSource.containedStatement).setJumpType(JumpType.BREAK);
                }
            }
        }

        return true;
    }

    public static void replaceRawSwitches(List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory) {
        List<Op03SimpleStatement> switchStatements = Functional.filter(in, new TypeFilter<RawSwitchStatement>(RawSwitchStatement.class));
        // Replace raw switch statements with switches and case statements inline.
        for (Op03SimpleStatement switchStatement : switchStatements) {
            replaceRawSwitch(switchStatement, in, blockIdentifierFactory);
        }
        // We've injected 'case' statements, sort to get them into the proper place.
        Collections.sort(in, new CompareByIndex());

//        Dumper d = new Dumper();
//        for (Op03SimpleStatement statement : in) {
//            statement.dumpInner(d);
//        }
//
        // For each of the switch statements, can we find a contiguous range which represents it?
        // (i.e. where the break statement vectors to).
        // for each case statement, we need to find a common successor, however there may NOT be
        // one, i.e. where all branches (or all bar one) cause termination (return/throw)

        // While we haven't yet done any analysis on loop bodies etc, we can make some fairly
        // simple assumptions, (which can be broken by an obfuscator)  - for each case statement
        // (except the last one) get the set of jumps which are to AFTER the start of the next statement.
        // Fall through doesn't count.
        // [These jumps may be legitimate breaks for the switch, or they may be breaks to enclosing statements.]
        // 1 ) If there are no forward jumps, pull the last case out, and make it fall through. (works for default/non default).
        // 2 ) If there are forward jumps, then it's possible that they're ALL to past the end of the switch statement
        //     However, if that's the case, it probable means that we've been obfuscated.  Take the earliest common one.
        //
        // Check each case statement for obfuscation - for all but the last case, all statements in the range [X -> [x+1)
        // without leaving the block.


        switchStatements = Functional.filter(in, new TypeFilter<SwitchStatement>(SwitchStatement.class));
        for (Op03SimpleStatement switchStatement : switchStatements) {
            // removePointlessSwitchDefault(switchStatement);
            examineSwitchContiguity(switchStatement, in);
        }

    }

    private static void optimiseForTypes(Op03SimpleStatement statement) {
        IfStatement ifStatement = (IfStatement) (statement.containedStatement);
        ifStatement.optimiseForTypes();
    }

    public static void optimiseForTypes(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> conditionals = Functional.filter(statements, new TypeFilter<IfStatement>(IfStatement.class));
        for (Op03SimpleStatement conditional : conditionals) {
            optimiseForTypes(conditional);
        }
    }


    private static boolean findHiddenIter(Statement statement, LValue lValue, Expression rValue) {
        AssignmentExpression needle = new AssignmentExpression(lValue, rValue, true);
        NOPSearchingExpressionRewriter finder = new NOPSearchingExpressionRewriter(needle);

        statement.rewriteExpressions(finder, statement.getContainer().getSSAIdentifiers());
        return finder.isFound();
    }

    private static void replaceHiddenIter(Statement statement, LValue lValue, Expression rValue) {
        AssignmentExpression needle = new AssignmentExpression(lValue, rValue, true);
        ExpressionReplacingRewriter finder = new ExpressionReplacingRewriter(needle, new LValueExpression(lValue));

        statement.rewriteExpressions(finder, statement.getContainer().getSSAIdentifiers());
    }

    /* Given a for loop
     *
     * array
     * bound = array.length
     * for ( index ; index < bound ; index++) {
     *   a = array[index]
     * }
     *
     * rewrite as
     *
     * for ( a : array ) {
     * }
     *
     * /however/ - it is only safe to do this if NEITHER index / bound / array are assigned to inside the loop.
     *
     * TODO : The tests in here are very rigid (and gross!), and need loosening up when it's working.
     */
    private static boolean rewriteArrayForLoop(final Op03SimpleStatement loop, List<Op03SimpleStatement> statements) {

        /*
         * loop should have one back-parent.
         */
        Op03SimpleStatement preceeding = findSingleBackSource(loop);
        if (preceeding == null) return false;

        ForStatement forStatement = (ForStatement) loop.containedStatement;

        WildcardMatch wildcardMatch = new WildcardMatch();

        if (!wildcardMatch.match(
                new AssignmentSimple(wildcardMatch.getLValueWildCard("iter"), new Literal(TypedLiteral.getInt(0))),
                forStatement.getInitial())) return false;

        LValue originalLoopVariable = wildcardMatch.getLValueWildCard("iter").getMatch();

        // Assignments are fiddly, as they can be assignmentPreChange or regular Assignment.
        AbstractAssignmentExpression assignment = forStatement.getAssignment();
        boolean incrMatch = assignment.isSelfMutatingOp1(originalLoopVariable, ArithOp.PLUS);
        if (!incrMatch) return false;

        if (!wildcardMatch.match(
                new ComparisonOperation(
                        new LValueExpression(originalLoopVariable),
                        new LValueExpression(wildcardMatch.getLValueWildCard("bound")),
                        CompOp.LT), forStatement.getCondition())) return false;

        LValue originalLoopBound = wildcardMatch.getLValueWildCard("bound").getMatch();

        // Bound should have been constructed RECENTLY, and should be an array length.
        // TODO: Let's just check the single backref from the for loop test.
        if (!wildcardMatch.match(
                new AssignmentSimple(originalLoopBound, new ArrayLength(new LValueExpression(wildcardMatch.getLValueWildCard("array")))),
                preceeding.containedStatement)) return false;

        LValue originalArray = wildcardMatch.getLValueWildCard("array").getMatch();

        Expression arrayStatement = new LValueExpression(originalArray);
        Op03SimpleStatement prepreceeding = null;
        /*
         * if we're following the JDK pattern, we'll have something assigned to array.
         */
        if (preceeding.sources.size() == 1) {
            if (wildcardMatch.match(
                    new AssignmentSimple(originalArray, wildcardMatch.getExpressionWildCard("value")),
                    preceeding.sources.get(0).containedStatement)) {
                prepreceeding = preceeding.sources.get(0);
                arrayStatement = wildcardMatch.getExpressionWildCard("value").getMatch();
            }
        }


        Op03SimpleStatement loopStart = loop.getTargets().get(0);
        // for the 'non-taken' branch of the test, we expect to find an assignment to a value.
        // TODO : This can be pushed into the loop, as long as it's not after a conditional.
        WildcardMatch.LValueWildcard sugariterWC = wildcardMatch.getLValueWildCard("sugariter");
        Expression arrIndex = new ArrayIndex(new LValueExpression(originalArray), new LValueExpression(originalLoopVariable));
        boolean hiddenIter = false;
        if (!wildcardMatch.match(
                new AssignmentSimple(sugariterWC, arrIndex),
                loopStart.containedStatement)) {
            // If the assignment's been pushed down into a conditional, we could have
            // if ((i = a[x]) > 3).  This is why we've avoided pushing that down. :(
            if (!findHiddenIter(loopStart.containedStatement, sugariterWC, arrIndex)) {
                return false;
            }
            hiddenIter = true;
        }

        LValue sugarIter = sugariterWC.getMatch();

        // It's probably valid.  We just have to make sure that array and index aren't assigned to anywhere in the loop
        // body.
        final BlockIdentifier forBlock = forStatement.getBlockIdentifier();
        List<Op03SimpleStatement> statementsInBlock = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.containedInBlocks.contains(forBlock);
            }
        });

        /*
         * It's not simple enough to check if they're assigned to - we also have to verify that i$ (for example ;) isn't
         * even USED anywhere else.
         */
        LValueUsageCollectorSimple usageCollector = new LValueUsageCollectorSimple();
        final Set<LValue> cantUpdate = SetFactory.newSet(originalArray, originalLoopBound, originalLoopVariable);

        for (Op03SimpleStatement inBlock : statementsInBlock) {
            if (inBlock == loopStart) continue;
            Statement inStatement = inBlock.containedStatement;
            inStatement.collectLValueUsage(usageCollector);
            for (LValue cantUse : cantUpdate) {
                if (usageCollector.isUsed(cantUse)) {
                    return false;
                }
            }
            LValue updated = inStatement.getCreatedLValue();
            if (updated == null) continue;
            if (cantUpdate.contains(updated)) {
                return false;
            }
        }

        /*
         * We shouldn't have to do this, because we should be doing this at a point where we've discovered
         * scope better (op04?), but now, verify that no reachable statements (do a dfs from the end point of
         * the loop with no retry) use either the iterator or the temp value without assigning them first.
         * (or are marked as being part of the block, as we've already verified them)
         * (or are the initial assignment statements).
         */
        final AtomicBoolean res = new AtomicBoolean();
        GraphVisitor<Op03SimpleStatement> graphVisitor = new GraphVisitorDFS<Op03SimpleStatement>(loop,
                new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                    @Override
                    public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                        if (!(loop == arg1 || arg1.getBlockIdentifiers().contains(forBlock))) {
                            // need to check it.
                            Statement inStatement = arg1.getStatement();

                            if (inStatement instanceof AssignmentSimple) {
                                AssignmentSimple assignmentSimple = (AssignmentSimple) inStatement;
                                if (cantUpdate.contains(assignmentSimple.getCreatedLValue())) return;
                            }
                            LValueUsageCollectorSimple usageCollector = new LValueUsageCollectorSimple();
                            inStatement.collectLValueUsage(usageCollector);
                            for (LValue cantUse : cantUpdate) {
                                if (usageCollector.isUsed(cantUse)) {
                                    res.set(true);
                                    return;
                                }
                            }
                        }
                        for (Op03SimpleStatement target : arg1.getTargets()) {
                            arg2.enqueue(target);
                        }
                    }
                });
        graphVisitor.process();
        if (res.get()) {
            return false;
        }


        loop.replaceStatement(new ForIterStatement(forBlock, sugarIter, arrayStatement));
        if (hiddenIter) {
            replaceHiddenIter(loopStart.containedStatement, sugariterWC.getMatch(), arrIndex);
        } else {
            loopStart.nopOut();
        }
        preceeding.nopOut();
        if (prepreceeding != null) {
            prepreceeding.nopOut();
        }

        return true;
    }

    public static void rewriteArrayForLoops(List<Op03SimpleStatement> statements) {
        for (Op03SimpleStatement loop : Functional.filter(statements, new TypeFilter<ForStatement>(ForStatement.class))) {
            rewriteArrayForLoop(loop, statements);
        }
    }

    /*
     * We're being called /after/ optimiseForTypes, so we expect an expression set of the form
     *
     * [x] = [y].iterator()
     * while ([x].hasNext()) {
     *   [a] = [x].next();
     * }
     */
    private static void rewriteIteratorWhileLoop(final Op03SimpleStatement loop, List<Op03SimpleStatement> statements) {
        WhileStatement whileStatement = (WhileStatement) loop.containedStatement;

        /*
         * loop should have one back-parent.
         */
        Op03SimpleStatement preceeding = findSingleBackSource(loop);
        if (preceeding == null) return;

        WildcardMatch wildcardMatch = new WildcardMatch();

        if (!wildcardMatch.match(
                new BooleanExpression(
                        wildcardMatch.getMemberFunction("hasnextfn", "hasNext", new LValueExpression(wildcardMatch.getLValueWildCard("iterable")))
                ),
                whileStatement.getCondition())) return;

        final LValue iterable = wildcardMatch.getLValueWildCard("iterable").getMatch();

        Op03SimpleStatement loopStart = loop.getTargets().get(0);
        // for the 'non-taken' branch of the test, we expect to find an assignment to a value.
        // TODO : This can be pushed into the loop, as long as it's not after a conditional.
        boolean isCastExpression = false;
        boolean hiddenIter = false;
        WildcardMatch.LValueWildcard sugariterWC = wildcardMatch.getLValueWildCard("sugariter");
        Expression nextCall = wildcardMatch.getMemberFunction("nextfn", "next", new LValueExpression(wildcardMatch.getLValueWildCard("iterable")));
        if (wildcardMatch.match(
                new AssignmentSimple(sugariterWC, nextCall),
                loopStart.containedStatement)) {
        } else if (wildcardMatch.match(
                new AssignmentSimple(sugariterWC,
                        wildcardMatch.getCastExpressionWildcard("cast", nextCall)),
                loopStart.containedStatement)) {
            // It's a cast expression - so we know that there's a type we might be able to push back up.
            isCastExpression = true;
        } else {
            // Try seeing if it's a hidden iter, which has been pushed inside a conditional
            if (!findHiddenIter(loopStart.containedStatement, sugariterWC, nextCall)) {
                return;
            }
            hiddenIter = true;
        }

        LValue sugarIter = wildcardMatch.getLValueWildCard("sugariter").getMatch();

        if (!wildcardMatch.match(
                new AssignmentSimple(wildcardMatch.getLValueWildCard("iterable"),
                        wildcardMatch.getMemberFunction("iterator", "iterator", wildcardMatch.getExpressionWildCard("iteratorsource"))),
                preceeding.containedStatement)) return;

        Expression iterSource = wildcardMatch.getExpressionWildCard("iteratorsource").getMatch();

        // It's probably valid.  We just have to make sure that array and index aren't assigned to anywhere in the loop
        // body.
        final BlockIdentifier blockIdentifier = whileStatement.getBlockIdentifier();
        List<Op03SimpleStatement> statementsInBlock = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.containedInBlocks.contains(blockIdentifier);
            }
        });

        /*
         * It's not simple enough to check if they're assigned to - we also have to verify that i$ (for example ;) isn't
         * even USED anywhere else.
         */
        LValueUsageCollectorSimple usageCollector = new LValueUsageCollectorSimple();
        for (Op03SimpleStatement inBlock : statementsInBlock) {
            if (inBlock == loopStart) continue;
            Statement inStatement = inBlock.containedStatement;
            inStatement.collectLValueUsage(usageCollector);
            if (usageCollector.isUsed(iterable)) {
                return;
            }
            LValue updated = inStatement.getCreatedLValue();
            if (updated == null) continue;
            if (updated.equals(sugarIter) || updated.equals(iterable)) {
                return;
            }
        }

        /*
         * We shouldn't have to do this, because we should be doing this at a point where we've discovered
         * scope better (op04?), but now, verify that no reachable statements (do a dfs from the end point of
         * the loop with no retry) use either the iterator or the temp value without assigning them first.
         * (or are marked as being part of the block, as we've already verified them)
         * (or are the initial assignment statements).
         */
        final AtomicBoolean res = new AtomicBoolean();
        GraphVisitor<Op03SimpleStatement> graphVisitor = new GraphVisitorDFS<Op03SimpleStatement>(loop,
                new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                    @Override
                    public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                        if (!(loop == arg1 || arg1.getBlockIdentifiers().contains(blockIdentifier))) {
                            // need to check it.
                            Statement inStatement = arg1.getStatement();

                            if (inStatement instanceof AssignmentSimple) {
                                AssignmentSimple assignmentSimple = (AssignmentSimple) inStatement;
                                if (iterable.equals(assignmentSimple.getCreatedLValue())) return;
                            }
                            LValueUsageCollectorSimple usageCollector = new LValueUsageCollectorSimple();
                            inStatement.collectLValueUsage(usageCollector);
                            if (usageCollector.isUsed(iterable)) {
                                res.set(true);
                                return;
                            }
                        }
                        for (Op03SimpleStatement target : arg1.getTargets()) {
                            arg2.enqueue(target);
                        }
                    }
                });
        graphVisitor.process();
        if (res.get()) {
            return;
        }

        /*
         * If it's a cast expression, we can try to push typing information back up.
         *
         * Doesn't work right now, as JavaPlaceholder is not referenced via an indirection.
         */
//        if (isCastExpression) {
//            CastExpression castExpression = wildcardMatch.getCastExpressionWildcard("cast").getMatch();
//            MemberFunctionInvokation iteratorCall = wildcardMatch.getMemberFunction("iterator").getMatch();
//            // if this is an iterator< placeholder > , we can try to push the type back up.
//            JavaTypeInstance iteratorType = iteratorCall.getInferredJavaType().getJavaTypeInstance();
//            if (iteratorType instanceof JavaGenericRefTypeInstance) {
//                JavaGenericRefTypeInstance genericIteratorType = (JavaGenericRefTypeInstance)iteratorType;
//                List<JavaTypeInstance> bindings = genericIteratorType.getGenericTypes();
//                JavaTypeInstance binding = null;
//                if (bindings.size() == 1 && ((binding = bindings.get(0)) instanceof JavaGenericPlaceholderTypeInstance)) {
//                    JavaGenericPlaceholderTypeInstance placeholderTypeInstance = (JavaGenericPlaceholderTypeInstance)binding;
//                    GenericTypeBinder binder = GenericTypeBinder.createEmpty();
//                    binder.suggestBindingFor(placeholderTypeInstance.getRawName(), castExpression.getInferredJavaType().getJavaTypeInstance());
//                    iteratorCall.getInferredJavaType().improveGeneric(genericIteratorType.getBoundInstance(binder));
//                }
//            }
//        }

        loop.replaceStatement(new ForIterStatement(blockIdentifier, sugarIter, iterSource));
        if (hiddenIter) {
            replaceHiddenIter(loopStart.containedStatement, sugariterWC.getMatch(), nextCall);
        } else {
            loopStart.nopOut();
        }
        preceeding.nopOut();
    }

    public static void rewriteIteratorWhileLoops(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> loops = Functional.filter(statements, new TypeFilter<WhileStatement>(WhileStatement.class));
        for (Op03SimpleStatement loop : loops) {
            rewriteIteratorWhileLoop(loop, statements);
        }
    }


    /*
     * Strictly speaking, this isn't true.....  We should verify elsewhere first that we don't push
     * operations through a monitorexit, as that will change the semantics.
     */
    private static boolean anyOpHasEffect(List<Op03SimpleStatement> ops) {
        for (Op03SimpleStatement op : ops) {
            Statement stm = op.getStatement();
            Class<?> stmcls = stm.getClass();
            if (stmcls == GotoStatement.class) continue;
            if (stmcls == ThrowStatement.class) continue;
            if (stmcls == CommentStatement.class) continue;
            if (stm instanceof ReturnStatement) continue;
            return true;
        }
        return false;
    }

    public static void findSynchronizedRange(final Op03SimpleStatement start, final Expression monitor) {
        final Set<Op03SimpleStatement> addToBlock = SetFactory.newSet();

        final Set<Op03SimpleStatement> foundExits = SetFactory.newSet();
        final Set<Op03SimpleStatement> extraNodes = SetFactory.newSet();
        /* Process all the parents until we find the monitorExit.
         * Note that this does NOT find statements which are 'orphaned', i.e.
         *
         * synch(foo) {
         *   try {
         *     bob
         *   } catch (e) {
         *     throw  <--- not reachable backwards from monitorexit,
         *   }
         *   monitorexit.
         *
         *   However, there must necessarily be a monitorexit before this throw.
         * }
         */

        final Set<BlockIdentifier> leaveExitsMutex = SetFactory.newSet();

        GraphVisitor<Op03SimpleStatement> marker = new GraphVisitorDFS<Op03SimpleStatement>(start.getTargets(),
                new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                    @Override
                    public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                        //
                        //

                        Statement statement = arg1.getStatement();

                        if (statement instanceof TryStatement) {
                            TryStatement tryStatement = (TryStatement) statement;
                            Set<Expression> tryMonitors = tryStatement.getMonitors();
                            if (tryMonitors.contains(monitor)) {
                                leaveExitsMutex.add(tryStatement.getBlockIdentifier());
                                List<Op03SimpleStatement> tgts = arg1.getTargets();
                                for (int x = 1, len = tgts.size(); x < len; ++x) {
                                    Statement innerS = tgts.get(x).getStatement();
                                    if (innerS instanceof CatchStatement) {
                                        leaveExitsMutex.add(((CatchStatement) innerS).getCatchBlockIdent());
                                    } else if (innerS instanceof FinallyStatement) {
                                        leaveExitsMutex.add(((FinallyStatement) innerS).getFinallyBlockIdent());
                                    }
                                }
                            }
                        }

                        if (statement instanceof MonitorExitStatement) {
                            if (monitor.equals(((MonitorExitStatement) statement).getMonitor())) {
                                foundExits.add(arg1);
                                addToBlock.add(arg1);
                                /*
                                 * If there's a return / throw / goto immediately after this, then we know that the brace
                                 * is validly moved.
                                 */
                                if (arg1.targets.size() == 1) {
                                    arg1 = arg1.targets.get(0);
                                    Statement targetStatement = arg1.containedStatement;
                                    if (targetStatement instanceof ReturnStatement ||
                                            targetStatement instanceof ThrowStatement ||
                                            targetStatement instanceof Nop ||
                                            targetStatement instanceof GotoStatement) {
                                        // TODO : Should perform a block check on targetStatement.
                                        extraNodes.add(arg1);
                                    }
                                }

                                return;
                            }
                        }
                        addToBlock.add(arg1);
                        if (SetUtil.hasIntersection(arg1.getBlockIdentifiers(), leaveExitsMutex)) {
                            for (Op03SimpleStatement tgt : arg1.getTargets()) {
                                if (SetUtil.hasIntersection(tgt.getBlockIdentifiers(), leaveExitsMutex)) {
                                    arg2.enqueue(tgt);
                                }
                            }
                        } else {
                            arg2.enqueue(arg1.getTargets());
                        }
                    }
                }
        );
        marker.process();

        /*
         * An extra pass, wherein we find all blocks which members of addtoblock are in.
         * (and the initial start is not in.)
         * This is because we need to handle synchronized blocks which may not fit into the natural
         * ordering.
         *
         * monitorenter
         * bip
         * if
         *   foo
         *   monitorexit
         *   bar
         * else
         *   bop
         *   monitorexit
         *   bam
         */
        addToBlock.remove(start);
        /*
         * find entries with same-block targets which are NOT in addToBlock, add them.
         */
        Set<Op03SimpleStatement> requiredComments = SetFactory.newSet();
        Iterator<Op03SimpleStatement> foundExitIter = foundExits.iterator();
        while (foundExitIter.hasNext()) {
            final Op03SimpleStatement foundExit = foundExitIter.next();
            final Set<BlockIdentifier> exitBlocks = SetFactory.newSet(foundExit.getBlockIdentifiers());
            exitBlocks.removeAll(start.getBlockIdentifiers());
            final List<Op03SimpleStatement> added = ListFactory.newList();
            GraphVisitor<Op03SimpleStatement> additional = new GraphVisitorDFS<Op03SimpleStatement>(foundExit, new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                @Override
                public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                    if (SetUtil.hasIntersection(exitBlocks, arg1.getBlockIdentifiers())) {
                        if (arg1 == foundExit) {
                            arg2.enqueue(arg1.getTargets());
                        } else if (addToBlock.add(arg1)) {
                            added.add(arg1);
                            arg2.enqueue(arg1.getTargets());
                        }
                    }
                }
            });
            additional.process();
            // If we had an effect, then we want to redesignate this monitor exit as a 'required comment'
            if (anyOpHasEffect(added)) {
                requiredComments.add(foundExit);
                foundExitIter.remove();
            }
        }


        MonitorEnterStatement monitorEnterStatement = (MonitorEnterStatement) (start.containedStatement);
        BlockIdentifier blockIdentifier = monitorEnterStatement.getBlockIdentifier();
        for (Op03SimpleStatement contained : addToBlock) {
            contained.containedInBlocks.add(blockIdentifier);
        }

        for (Op03SimpleStatement exit : foundExits) {
            exit.nopOut();
        }

        for (Op03SimpleStatement exit : requiredComments) {
            exit.replaceStatement(new CommentStatement("MONITOREXIT " + exit));
        }

        /* For the extra nodes, if ALL the sources are in the block, we add the extranode
         * to the block.  This pulls returns/throws into the block, but keeps them out
         * if they're targets for a conditional outside the block.
         */
        for (Op03SimpleStatement extra : extraNodes) {
            boolean allParents = true;
            for (Op03SimpleStatement source : extra.sources) {
                if (!source.containedInBlocks.contains(blockIdentifier)) {
                    allParents = false;
                }
            }
            if (allParents) {
                extra.containedInBlocks.add(blockIdentifier);
            }
        }

    }

    /*
    * We make a (dangerous?) assumption here - that the monitor entered is the same one as exited.
    * Can JVM spec be read to allow
    *
    * a = x;
    * b = x;
    * enter(a)
    * exit(b) ?
    *
    * Since monitorenter/exit must be paired (it's counted) we don't have to worry (much!) about monitorenter in a loop without
    * exit.
    *
    * (might be a good anti-decompiler technique though!)
    *
    * What would be nasty is a switch statement which enters on one branch and exits on another...
    */
    public static void findSynchronizedBlocks(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> enters = Functional.filter(statements, new TypeFilter<MonitorEnterStatement>(MonitorEnterStatement.class));
        // Each exit can be tied to one enter, which is the first one found by
        // walking code backwards and not passing any other exit/enter for this var.
        // (Every exit from a synchronised block has to exit, so if there's any possibiliy of an exception... )

        for (Op03SimpleStatement enter : enters) {
            MonitorEnterStatement monitorExitStatement = (MonitorEnterStatement) enter.containedStatement;

            findSynchronizedRange(enter, monitorExitStatement.getMonitor());
        }
    }

    /*
     * This is a dangerous tidy-up operation.  Should only do it if we're falling back.
     */
    public static void rejoinBlocks(List<Op03SimpleStatement> statements) {
        Set<BlockIdentifier> lastBlocks = SetFactory.newSet();
        Set<BlockIdentifier> haveLeft = SetFactory.newSet();
        // We blacklist blocks we can't POSSIBLY be in - i.e. after a catch block has started, we can't POSSIBLY
        // be in its try block.
        Set<BlockIdentifier> blackListed = SetFactory.newSet();

        for (int x = 0, len = statements.size(); x < len; ++x) {
            Op03SimpleStatement stm = statements.get(x);
            Statement stmInner = stm.getStatement();
            if (stmInner instanceof CatchStatement) {
                CatchStatement catchStatement = (CatchStatement) stmInner;
                for (ExceptionGroup.Entry entry : catchStatement.getExceptions()) {
                    blackListed.add(entry.getTryBlockIdentifier());
                }
            }
            // If we're in any blocks which we have left, then we need to backfill.
            Set<BlockIdentifier> blocks = stm.getBlockIdentifiers();
            blocks.removeAll(blackListed);

            for (BlockIdentifier ident : blocks) {
//                if (ident.getBlockType() == BlockType.CASE ||
//                    ident.getBlockType() == BlockType.SWITCH) {
//                    blackListed.add(ident);
//                    continue;
//                }
                if (haveLeft.contains(ident)) {
                    // Backfill, remove from haveLeft.
                    for (int y = x - 1; y >= 0; --y) {
                        Op03SimpleStatement backFill = statements.get(y);
                        if (!backFill.getBlockIdentifiers().add(ident)) break;
                    }
                }
            }
            for (BlockIdentifier wasIn : lastBlocks) {
                if (!blocks.contains(wasIn)) haveLeft.add(wasIn);
            }
            lastBlocks = blocks;
        }
    }

    private static void removePointlessSwitchDefault(Op03SimpleStatement swtch) {
        SwitchStatement switchStatement = (SwitchStatement) swtch.getStatement();
        BlockIdentifier switchBlock = switchStatement.getSwitchBlock();
        // If one of the targets is a "default", and it's definitely a target for this switch statement...
        // AND it hasn't been marked as belonging to the block, remove it.
        // A default with no code is of course equivalent to no default.
        for (Op03SimpleStatement tgt : swtch.getTargets()) {
            Statement statement = tgt.getStatement();
            if (statement instanceof CaseStatement) {
                CaseStatement caseStatement = (CaseStatement) statement;
                if (caseStatement.getSwitchBlock() == switchBlock) {
                    if (caseStatement.isDefault()) {
                        if (tgt.targets.size() != 1) return;
                        Op03SimpleStatement afterTgt = tgt.targets.get(0);
                        if (afterTgt.containedInBlocks.contains(switchBlock)) {
                            return;
                        } else {
                            // We can remove this.
                            tgt.nopOut();
                            return;
                        }
                    }
                }
            }
        }
    }

    public static void removePointlessSwitchDefaults(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> switches = Functional.filter(statements, new TypeFilter<SwitchStatement>(SwitchStatement.class));

        for (Op03SimpleStatement swtch : switches) {
            removePointlessSwitchDefault(swtch);
        }
    }

    private static boolean resugarAnonymousArray(Op03SimpleStatement newArray, List<Op03SimpleStatement> statements) {
        AssignmentSimple assignmentSimple = (AssignmentSimple) newArray.containedStatement;
        WildcardMatch start = new WildcardMatch();
        if (!start.match(
                new AssignmentSimple(start.getLValueWildCard("array"), start.getNewArrayWildCard("def")),
                assignmentSimple
        )) {
            throw new ConfusedCFRException("Expecting new array");
        }
        /*
         * If it's not a literal size, ignore.
         */
        LValue arrayLValue = start.getLValueWildCard("array").getMatch();
        if (!(arrayLValue instanceof StackSSALabel || arrayLValue instanceof LocalVariable)) {
            return false;
        }
        LValue array = arrayLValue;
        AbstractNewArray arrayDef = start.getNewArrayWildCard("def").getMatch();
        Expression dimSize0 = arrayDef.getDimSize(0);
        if (!(dimSize0 instanceof Literal)) return false;
        Literal lit = (Literal) dimSize0;
        if (lit.getValue().getType() != TypedLiteral.LiteralType.Integer) return false;
        int bound = (Integer) lit.getValue().getValue();

        Op03SimpleStatement next = newArray;
        List<Expression> anon = ListFactory.newList();
        List<Op03SimpleStatement> anonAssigns = ListFactory.newList();
        Expression arrayExpression = null;
        if (array instanceof StackSSALabel) {
            arrayExpression = new StackValue((StackSSALabel) array);
        } else {
            arrayExpression = new LValueExpression(array);
        }
        for (int x = 0; x < bound; ++x) {
            if (next.targets.size() != 1) {
                return false;
            }
            next = next.targets.get(0);
            WildcardMatch testAnon = new WildcardMatch();
            Literal idx = new Literal(TypedLiteral.getInt(x));
            if (!testAnon.match(
                    new AssignmentSimple(
                            new ArrayVariable(new ArrayIndex(arrayExpression, idx)),
                            testAnon.getExpressionWildCard("val")),
                    next.containedStatement)) {
                return false;
            }
            anon.add(testAnon.getExpressionWildCard("val").getMatch());
            anonAssigns.add(next);
        }
        AssignmentSimple replacement = new AssignmentSimple(arrayLValue.getInferredJavaType(), assignmentSimple.getCreatedLValue(), new NewAnonymousArray(arrayDef.getInferredJavaType(), arrayDef.getNumDims(), anon, false));
        newArray.replaceStatement(replacement);
        if (array instanceof StackSSALabel) {
            StackEntry arrayStackEntry = ((StackSSALabel) array).getStackEntry();
            for (Op03SimpleStatement create : anonAssigns) {
                arrayStackEntry.decrementUsage();
            }
        }
        for (Op03SimpleStatement create : anonAssigns) {
            create.nopOut();
        }
        return true;
    }

    /*
     * Search for
     *
     * stk = new X[N];
     * stk[0] = a
     * stk[1] = b
     * ...
     * stk[N-1] = c
     *
     * transform into stk = new X{ a,b, .. c }
     *
     * (it's important that stk is a stack label, so we don't allow an RValue to reference it inside the
     * array definition!)
     */
    public static void resugarAnonymousArrays(List<Op03SimpleStatement> statements) {
        boolean success = false;
        do {
            List<Op03SimpleStatement> assignments = Functional.filter(statements, new TypeFilter<AssignmentSimple>(AssignmentSimple.class));
            // filter for structure now
            assignments = Functional.filter(assignments, new Predicate<Op03SimpleStatement>() {
                @Override
                public boolean test(Op03SimpleStatement in) {
                    AssignmentSimple assignmentSimple = (AssignmentSimple) in.containedStatement;
                    WildcardMatch wildcardMatch = new WildcardMatch();
                    return (wildcardMatch.match(
                            new AssignmentSimple(wildcardMatch.getLValueWildCard("array"), wildcardMatch.getNewArrayWildCard("def", 1, null)),
                            assignmentSimple
                    ));
                }
            });
            success = false;
            for (Op03SimpleStatement assignment : assignments) {
                success |= resugarAnonymousArray(assignment, statements);
            }
            if (success) {
                condenseLValues(statements);
            }
        }
        while (success);
    }

    public static void inferGenericObjectInfoFromCalls(List<Op03SimpleStatement> statements) {
        // memberFunctionInvokations will either be wrapped in ExpressionStatement or SimpleAssignment.
        List<MemberFunctionInvokation> memberFunctionInvokations = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            Statement contained = statement.getStatement();
            if (contained instanceof ExpressionStatement) {
                Expression e = ((ExpressionStatement) contained).getExpression();
                if (e instanceof MemberFunctionInvokation) {
                    memberFunctionInvokations.add((MemberFunctionInvokation) e);
                }
            } else if (contained instanceof AssignmentSimple) {
                Expression e = ((AssignmentSimple) contained).getRValue();
                if (e instanceof MemberFunctionInvokation) {
                    memberFunctionInvokations.add((MemberFunctionInvokation) e);
                }
            }
        }
        Map<Integer, List<MemberFunctionInvokation>> byTypKey = MapFactory.newTreeMap();
        Functional.groupToMapBy(memberFunctionInvokations, byTypKey, new UnaryFunction<MemberFunctionInvokation, Integer>() {
            @Override
            public Integer invoke(MemberFunctionInvokation arg) {
                return arg.getObject().getInferredJavaType().getLocalId();
            }
        });

        invokationGroup:
        for (Map.Entry<Integer, List<MemberFunctionInvokation>> entry : byTypKey.entrySet()) {
            Integer key = entry.getKey();
            List<MemberFunctionInvokation> invokations = entry.getValue();
            if (invokations.isEmpty()) continue;

            Expression obj0 = invokations.get(0).getObject();
            JavaTypeInstance type = obj0.getInferredJavaType().getJavaTypeInstance();
            if (!(type instanceof JavaGenericBaseInstance)) continue;
            JavaGenericBaseInstance genericType = (JavaGenericBaseInstance) type;
            if (!genericType.hasUnbound()) continue;

            GenericTypeBinder gtb0 = getGtb(invokations.get(0));
            if (gtb0 == null) continue invokationGroup;
            for (int x = 1, len = invokations.size(); x < len; ++x) {
                GenericTypeBinder gtb = getGtb(invokations.get(x));
                if (gtb == null) {
                    continue invokationGroup;
                }
                gtb0 = gtb0.mergeWith(gtb, true);
                if (gtb0 == null) {
                    continue invokationGroup;
                }
            }
            obj0.getInferredJavaType().deGenerify(gtb0.getBindingFor(obj0.getInferredJavaType().getJavaTypeInstance()));
        }
    }

    static GenericTypeBinder getGtb(MemberFunctionInvokation m) {
        return m.getMethodPrototype().getTypeBinderFor(m.getArgs());
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
