package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.parse.*;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.ArrayVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AccountingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.stack.StackEntry;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitch;
import org.benf.cfr.reader.bytecode.opcode.DecodedSwitchEntry;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.functors.UnaryFunction;
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
    private SSAIdentifiers ssaIdentifiers;
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
        this.ssaIdentifiers = new SSAIdentifiers();
        this.containedInBlocks.addAll(original.getContainedInTheseBlocks());
        statement.setContainer(this);
    }

    private Op03SimpleStatement(Set<BlockIdentifier> containedIn, Statement statement, InstrIndex index) {
        this.containedStatement = statement;
        this.isNop = false;
        this.index = index;
        this.ssaIdentifiers = new SSAIdentifiers();
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
        if (targets.size() <= idx) throw new ConfusedCFRException("Trying to get invalid target " + idx);
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
    public SSAIdentifiers getSSAIdentifiers() {
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

    @Override
    public Set<BlockIdentifier> getBlocksEnded() {
        return immediatelyAfterBlocks;
    }

    @Override
    public void copyBlockInformationFrom(StatementContainer other) {
        this.immediatelyAfterBlocks.addAll(other.getBlocksEnded());
        this.containedInBlocks.addAll(other.getBlockIdentifiers());
    }

    private boolean isNop() {
        return isNop;
    }

    public void replaceTarget(Op03SimpleStatement oldTarget, Op03SimpleStatement newTarget) {
        int index = targets.indexOf(oldTarget);
        if (index == -1) {
            throw new ConfusedCFRException("Invalid target");
        }
        targets.set(index, newTarget);
    }

    private void replaceSingleSourceWith(Op03SimpleStatement oldSource, List<Op03SimpleStatement> newSources) {
        if (!sources.remove(oldSource)) throw new ConfusedCFRException("Invalid source");
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

    private void setIndex(InstrIndex index) {
        this.index = index;
    }

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

    private void markFirstStatementInBlock(BlockIdentifier blockIdentifier) {
        if (this.firstStatementInThisBlock != null) {
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
                throw new ConfusedCFRException("Can't sort instructions");
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

    private void collectLocallyMutatedVariables(SSAIdentifierFactory ssaIdentifierFactory) {
        this.ssaIdentifiers = containedStatement.collectLocallyMutatedVariables(ssaIdentifierFactory);
    }

    public static void assignSSAIdentifiers(List<Op03SimpleStatement> statements) {
        SSAIdentifierFactory ssaIdentifierFactory = new SSAIdentifierFactory();
        for (Op03SimpleStatement statement : statements) {
            statement.collectLocallyMutatedVariables(ssaIdentifierFactory);
        }

        LinkedList<Op03SimpleStatement> toProcess = ListFactory.newLinkedList();
        toProcess.addAll(statements);
        while (!toProcess.isEmpty()) {
            Op03SimpleStatement statement = toProcess.remove();
            SSAIdentifiers ssaIdentifiers = statement.ssaIdentifiers;
            boolean changed = false;
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
    public static void condenseConstruction(List<Op03SimpleStatement> statements) {
        CreationCollector creationCollector = new CreationCollector();
        for (Op03SimpleStatement statement : statements) {
            statement.findCreation(creationCollector);
        }
        creationCollector.condenseConstructions();
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

    /* If there is a chain of assignments before this conditional,
     * AND following single parents back, there is only conditionals and assignments,
     * AND this chain terminates in a back jump.....
     */
    private static boolean appropriateForIfAssignmentCollapse(Op03SimpleStatement statement) {
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

    // a=x
    // b=y
    // if (b==a)
    //
    // --> if ((b=x)==(a=y))
    private static void collapseAssignmentsIntoConditional(Op03SimpleStatement ifStatement) {
        logger.fine("Collapse assignment into conditional " + ifStatement);
        if (!appropriateForIfAssignmentCollapse(ifStatement)) return;

        IfStatement innerIf = (IfStatement) ifStatement.containedStatement;
        ConditionalExpression conditionalExpression = innerIf.getCondition();
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
            AbstractAssignmentExpression assignmentExpression = assignment.getInliningExpression();
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
    public static void collapseAssignmentsIntoConditionals(List<Op03SimpleStatement> statements) {
        // find all conditionals.
        List<Op03SimpleStatement> ifStatements = Functional.filter(statements, new TypeFilter<IfStatement>(IfStatement.class));
        for (Op03SimpleStatement statement : ifStatements) {
            collapseAssignmentsIntoConditional(statement);
        }
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


    /* Remove pointless jumps 
    *
    * Normalise code by removing jumps which have been introduced to confuse.
    */
    public static void removePointlessJumps(List<Op03SimpleStatement> statements) {
        // Do this pass first, as it needs spatial locality.
        int size = statements.size() - 1;
        for (int x = 0; x < size; ++x) {
            Op03SimpleStatement maybeJump = statements.get(x);
            if (maybeJump.containedStatement instanceof GotoStatement &&
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
                    if (innerZStatement instanceof GotoStatement) {
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

    public static void rewriteBreakStatements(List<Op03SimpleStatement> statements) {
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
                    if (targetStatement.thisComparisonBlock != null) {  // Jumps to the comparison test of a WHILE
                        // Continue loopBlock, IF this statement is INSIDE that block.
                        if (BlockIdentifier.blockIsOneOf(targetStatement.thisComparisonBlock, statement.containedInBlocks)) {
                            jumpingStatement.setJumpType(JumpType.CONTINUE);
                        }
                    } else if (targetStatement.getBlockStarted() != null &&
                            targetStatement.getBlockStarted().getBlockType() == BlockType.UNCONDITIONALDOLOOP) {
                        if (BlockIdentifier.blockIsOneOf(targetStatement.getBlockStarted(), statement.containedInBlocks)) {
                            jumpingStatement.setJumpType(JumpType.CONTINUE);
                        }
                    } else if (!targetStatement.immediatelyAfterBlocks.isEmpty()) {
                        BlockIdentifier outermostContainedIn = BlockIdentifier.getOutermostContainedIn(targetStatement.immediatelyAfterBlocks, statement.containedInBlocks);
                        // Break to the outermost block.
                        if (outermostContainedIn != null) {
                            jumpingStatement.setJumpType(JumpType.BREAK);
                        }
                    }
                }
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

    // Find simple loops.
    // Identify distinct set of backjumps (b1,b2), which jump back to somewhere (p) which has a forward
    // jump to somewhere which is NOT a /DIRECT/ parent of the backjumps (i.e. has to go through p)
    // p must be a direct parent of all of (b1,b2)
    public static void identifyLoops1(List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory) {
        // Find back references.
        // Verify that they belong to jump instructions (otherwise something has gone wrong)
        // (if, goto).
        List<Op03SimpleStatement> backjumps = Functional.filter(statements, new HasBackJump());
        List<Op03SimpleStatement> starts = Functional.uniqAll(Functional.map(backjumps, new GetBackJump()));
        /* Each of starts is the target of a back jump.
         * Consider each of these seperately, and for each of these verify
         * that it contains a forward jump to something which is not a parent except through p.
         */
        Map<BlockIdentifier, Op03SimpleStatement> blockEndsCache = MapFactory.newMap();
        Collections.sort(starts, new CompareByIndex());

        for (Op03SimpleStatement start : starts) {
            if (considerAsWhileLoopStart(start, statements, blockIdentifierFactory, blockEndsCache)) continue;
            considerAsDoLoopStart(start, statements, blockIdentifierFactory, blockEndsCache);
        }

    }

    private static class HasBackJump implements Predicate<Op03SimpleStatement> {
        @Override
        public boolean test(Op03SimpleStatement in) {
            InstrIndex inIndex = in.getIndex();
            List<Op03SimpleStatement> targets = in.getTargets();
            for (Op03SimpleStatement target : targets) {
                if (target.getIndex().compareTo(inIndex) < 0) {
                    if (!(in.containedStatement instanceof JumpingStatement)) {
                        if (in.containedStatement instanceof JSRRetStatement) {
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
                if (target.getIndex().compareTo(inIndex) < 0) {
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


    private static boolean considerAsDoLoopStart(final Op03SimpleStatement start, final List<Op03SimpleStatement> statements,
                                                 BlockIdentifierFactory blockIdentifierFactory,
                                                 Map<BlockIdentifier, Op03SimpleStatement> postBlockCache) {

        final InstrIndex startIndex = start.getIndex();
        logger.fine("Is this a do loop start ? " + start);
        List<Op03SimpleStatement> backJumpSources = start.getSources();
        if (backJumpSources.isEmpty()) {
            throw new ConfusedCFRException("Node doesn't have ANY sources! " + start);
        }
        backJumpSources = Functional.filter(backJumpSources, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getIndex().compareTo(startIndex) > 0;
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
                return false;
            }
        }
//        if (!conditional) return false;

        int startIdx = statements.indexOf(start);
        int endIdx = statements.indexOf(lastJump);

        if (startIdx >= endIdx) return false;

        BlockIdentifier blockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(conditional ? BlockType.DOLOOP : BlockType.UNCONDITIONALDOLOOP);

        /* Given that the potential statements inside this block are idxConditional+1 -> idxAfterEnd-1, [a->b]
        * there SHOULD be a prefix set (or all) in here which is addressable from idxConditional+1 without leaving the
        * range [a->b].  Determine this.  If we have reachable entries which aren't in the prefix, we can't cope.
        */
        try {
            validateAndAssignLoopIdentifier(statements, startIdx, endIdx + 1, blockIdentifier);
        } catch (CannotPerformDecode e) {
            // Can't perform this optimisation.
            return false;
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

        statements.add(statements.indexOf(start), doStatement);
        lastJump.markBlockStatement(blockIdentifier, null, lastJump, statements);
        start.markFirstStatementInBlock(blockIdentifier);
        postBlock.markPostBlock(blockIdentifier);
        postBlockCache.put(blockIdentifier, postBlock);

        return true;

    }

    /* Is the first conditional jump NOT one of the sources of start?
    * Take the target of the first conditional jump - is it somehwhere which is not reachable from
    * any of the forward sources of start without going through start?
    *
    * If so we've probably got a for/while loop.....
    * decode both as a while loop, we can convert it into a for later.
    */
    private static boolean considerAsWhileLoopStart(final Op03SimpleStatement start, final List<Op03SimpleStatement> statements,
                                                    BlockIdentifierFactory blockIdentifierFactory,
                                                    Map<BlockIdentifier, Op03SimpleStatement> postBlockCache) {
        final InstrIndex startIndex = start.getIndex();
        logger.fine("Is this a while loop start ? " + start);
        List<Op03SimpleStatement> backJumpSources = start.getSources();
        backJumpSources = Functional.filter(backJumpSources, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getIndex().compareTo(startIndex) > 0;
            }
        });
        Collections.sort(backJumpSources, new CompareByIndex());
        Op03SimpleStatement conditional = findFirstConditional(start);
        if (conditional == null) {
            // No conditional before we have a branch?  Probably a do { } while. 
            logger.info("Can't find a conditional");
            return false;
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
         * This could be broken by a decompiler easily.  We need a transform state which
         * normalises the code so the jump out is the explicit jump.
         * TODO : Could do this by finding which one of the targets of the condition is NOT reachable
         * TODO : by going back from each of the backJumpSources to conditional
         *
         * TODO: This might give us something WAY past the end of the loop, if the next instruction is to
         * jump past a catch block.....
         */
        Op03SimpleStatement loopBreak = conditionalTargets.get(1);

        if (loopBreak.getIndex().compareTo(lastJump.getIndex()) <= 0) {
            // The conditional doesn't take us to after the last back jump, i.e. it's not a while {} loop.
            // ... unless it's an inner while loop continuing to a prior loop.
            if (loopBreak.getIndex().compareTo(startIndex) >= 0) {
                return false;
            }
        }

        if (start != conditional) {
            // We'll have problems - there are actions taken inside the conditional.
            return false;
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
                return false;
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
            return false;
        }
        BlockIdentifier blockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.WHILELOOP);

        /* Given that the potential statements inside this block are idxConditional+1 -> idxAfterEnd-1, [a->b]
        * there SHOULD be a prefix set (or all) in here which is addressable from idxConditional+1 without leaving the
        * range [a->b].  Determine this.  If we have reachable entries which aren't in the prefix, we can't cope.
        */
        int lastIdx = validateAndAssignLoopIdentifier(statements, idxConditional + 1, idxAfterEnd, blockIdentifier);

        Op03SimpleStatement lastInBlock = statements.get(lastIdx);
        Op03SimpleStatement blockEnd = statements.get(idxAfterEnd);
        start.markBlockStatement(blockIdentifier, lastInBlock, blockEnd, statements);
        statements.get(idxConditional + 1).markFirstStatementInBlock(blockIdentifier);
        blockEnd.markPostBlock(blockIdentifier);
        postBlockCache.put(blockIdentifier, blockEnd);
        return true;
    }

    private static int getFarthestReachableInRange(List<Op03SimpleStatement> statements, int start, int afterEnd) {
        Map<Op03SimpleStatement, Integer> instrToIdx = MapFactory.newMap();
        for (int x = start; x < afterEnd; ++x) {
            Op03SimpleStatement statement = statements.get(x);
            instrToIdx.put(statement, x);
        }

        Set<Integer> reachableNodes = SetFactory.newSet();
        GraphVisitorReachableInThese graphVisitorCallee = new GraphVisitorReachableInThese(reachableNodes, instrToIdx);
        GraphVisitor<Op03SimpleStatement> visitor = new GraphVisitorDFS<Op03SimpleStatement>(statements.get(start), graphVisitorCallee);
        visitor.process();

        final int first = start;
        int last = -1;
        boolean foundLast = false;

        for (int x = first; x < afterEnd; ++x) {
            if (reachableNodes.contains(x) || statements.get(x).isNop()) {
                if (foundLast) {
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

    private static int validateAndAssignLoopIdentifier(List<Op03SimpleStatement> statements, int idxTestStart, int idxAfterEnd, BlockIdentifier blockIdentifier) {
        int last = getFarthestReachableInRange(statements, idxTestStart, idxAfterEnd);

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
            if (iValue == 1) return Troolean.FIRST;
            if (iValue == 0) return Troolean.SECOND;
            return Troolean.NEITHER;
        }

        private boolean isPointlessBoolean() {
            if (!(e1.getInferredJavaType().getRawType() == RawJavaType.BOOLEAN &&
                    e2.getInferredJavaType().getRawType() == RawJavaType.BOOLEAN)) return false;

            if (isOneOrZeroLiteral(e1) != Troolean.FIRST) return false;
            if (isOneOrZeroLiteral(e2) != Troolean.SECOND) return false;
            return true;
        }
    }

    private static class TypeFilter<T> implements Predicate<Op03SimpleStatement> {
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
            notTakenTarget.addSource(taken);
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
                        Op03SimpleStatement oldTarget = ifStatement.targets.get(1);
                        newJump.addTarget(oldTarget);
                        ifStatement.replaceTarget(oldTarget, newJump);
                        oldTarget.replaceSource(ifStatement, newJump);
                        statements.add(idxCurrent, newJump);
                        return true;
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
                        if (target == eventualTarget) {
                            targets.set(x, stmtLastBlockRewrite);
                            eventualTarget.replaceSource(statementCurrent, stmtLastBlockRewrite);
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
        if (!(blocksAtStart.containsAll(blocksAtEnd) && blocksAtEnd.size() == blocksAtStart.size())) return takenAction;

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
                List<Long> sources = stackEntry.getSources();
                stackEntry.removeSource(sources.get(sources.size() - 1));
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

            logger.info("IfStatement targets : " + ifStatement.targets);

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
                throw new IllegalStateException();
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
            for (Op03SimpleStatement forwardIf : forwardIfs) {
                success |= considerAsSimpleIf(forwardIf, statements, blockIdentifierFactory, ignoreTheseJumps);
            }
        } while (success);
    }


    private static Op03SimpleStatement setFinalBlockEnd(Op03SimpleStatement previous, Op03SimpleStatement thisGuess) {
        if (previous == null) return thisGuess;
        if (previous != thisGuess) return null;
        return previous;
    }

    private static class IfAndStatements {
        private final Op03SimpleStatement ifStatement;
        private final List<Op03SimpleStatement> statements;
        private final Op03SimpleStatement leaveBranch;

        IfAndStatements(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> statements, Op03SimpleStatement leaveBranch) {
            this.ifStatement = ifStatement;
            this.statements = statements;
            this.leaveBranch = leaveBranch;
        }

        public Op03SimpleStatement getIfStatement() {
            return ifStatement;
        }

        public List<Op03SimpleStatement> getStatements() {
            return statements;
        }

        public Op03SimpleStatement getLeaveBranch() {
            return leaveBranch;
        }
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

    public static boolean identifyFinally(Op03SimpleStatement in, List<Op03SimpleStatement> allStatements) {

        if (!(in.getStatement() instanceof TryStatement)) return false;
        TryStatement tryStatement = (TryStatement) in.getStatement();
        final BlockIdentifier tryBlockIdentifier = tryStatement.getBlockIdentifier();

        /*
         * We only need worry about try statements which have a 'Throwable' handler.
         */
        List<Op03SimpleStatement> targets = in.getTargets();
        List<Op03SimpleStatement> catchStarts = Functional.filter(targets, new TypeFilter<CatchStatement>(CatchStatement.class));
        Set<Op03SimpleStatement> possibleCatches = SetFactory.newSet();
        for (Op03SimpleStatement catchS : catchStarts) {
            CatchStatement catchStatement = (CatchStatement) catchS.getStatement();
            List<ExceptionGroup.Entry> exceptions = catchStatement.getExceptions();
            for (ExceptionGroup.Entry exception : exceptions) {
                if (exception.getExceptionGroup().getTryBlockIdentifier() == tryBlockIdentifier) {
                    JavaRefTypeInstance catchType = exception.getCatchType();
                    if (TypeConstants.throwableName.equals(catchType.getRawName())) {
                        possibleCatches.add(catchS);
                    }
                }
            }
        }
        if (possibleCatches.isEmpty()) return false;
        /* A catch block is a better candidate than another one, if it is contained inside the other catch blocks.
         * A lame heuristic here is simply to count the block identifiers.
         */
        int maxIdents = -1;
        Op03SimpleStatement best = null;
        for (Op03SimpleStatement test : possibleCatches) {
            int testIdents = test.getBlockIdentifiers().size();
            if (testIdents > maxIdents) {
                best = test;
                maxIdents = testIdents;
            }
        }
        if (best == null) return false;

        Set<BlockIdentifier> peerTest = SetFactory.newSet();
        for (Op03SimpleStatement catchStart : catchStarts) {
            peerTest.add(((CatchStatement) catchStart.getStatement()).getCatchBlockIdent());
        }
        peerTest.add(tryStatement.getBlockIdentifier());

        /*
         * Now.... find all the other try blocks that this catch block is called from.  If they are not nested in
         * the original try block, or one of the catches of the original try block,
         * then they're peers, which means that they've been split out from the original
         * try block in order to inline finally statements between them!
         *
         * Once we've removed the finally statements, we'll need to join up these blocks.
         */
        Map<CompositeBlockIdentifierKey, Set<Op03SimpleStatement>> peerTries = MapFactory.newLazyMap(new UnaryFunction<CompositeBlockIdentifierKey, Set<Op03SimpleStatement>>() {
            @Override
            public Set<Op03SimpleStatement> invoke(CompositeBlockIdentifierKey arg) {
                return SetFactory.newSet();
            }
        });

        List<Op03SimpleStatement> catchSources = best.getSources();
        Set<Op03SimpleStatement> allTries = SetFactory.newSet();
        for (Op03SimpleStatement catchSource : catchSources) {
            if (catchSource.getStatement() instanceof TryStatement) {
                peerTries.get(new CompositeBlockIdentifierKey(catchSource.getBlockIdentifiers())).add(catchSource);
                allTries.add(catchSource);
            }
        }

        /* At worst, peerTries should contain 'in' */

        /*
         * Ok.  Now, we have code which will either end in a throw (of the caught variable) (normal finally)
         * (in which case we discount that throw)
         * or something else.
         */
        CatchStatement finallyCatch = (CatchStatement) best.getStatement();
        final BlockIdentifier finallyIdent = finallyCatch.getCatchBlockIdent();

        /*
         * We expect the finallyCatch to be of the form
         *
         * catch (Throwable t) {
         *   // BLOCK
         *   throw t.
         * }
         */
        if (best.getTargets().size() != 1) {
            return false;
        }
        Op03SimpleStatement finallyStart = best.getTargets().get(0);
        if (!finallyStart.getBlockIdentifiers().contains(finallyIdent)) {
            return false;
        }
        /*
         * If the last statement in the catch block is a throw OF THE caught throwable,
         * then we'll assume that's generated.  Anything else, it's (maybe) a finally
         * with a non-standard escape.
         *
         * Find the statement(s) which exit this block.
         */
        List<Op03SimpleStatement> inBlock = Functional.filter(allStatements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getBlockIdentifiers().contains(finallyIdent);
            }
        });
        Op03SimpleStatement orderLast = inBlock.get(inBlock.size() - 1);

        Statement testThrow = new ThrowStatement(new LValueExpression(finallyCatch.getCreatedLValue()));
        if (!testThrow.equals(orderLast.getStatement())) {
            orderLast = null;
        }

        /*
         * We also need to find (so we can ignore from comparison) the throw at the end of the finally.
         * (it HAS to throw the caught var, unless we have a finally with a throw).
         */
        FinallyHelper finallyHelper = new FinallyHelper(finallyStart, finallyIdent, orderLast, best);

        /*
         * Now, consider all of the entries in allTries - we need to make sure that every exit point for them has
         * an identical block to the one begun at finallyStart.
         */
        FinallyResultAndUsages finallyResultAndUsages = new FinallyResultAndUsages();
        for (Set<Op03SimpleStatement> peerSet : peerTries.values()) {
            if (!verifyPeerSet(peerSet, finallyHelper, allTries, finallyResultAndUsages)) {
                return false;
            }
        }
        /*
         * Ok.  Now, remove all but the original 'finally' catch block, and, for each peerset, try to collapse
         * the tries into one.
         *
         * When we remove the block, if we end up with a return / goto sitting directly outside a try block, pull it
         * in before we collapse tries.
         */
        finallyResultAndUsages.clearCopies(allStatements);

        /*
         * Now, for each peer set, if the only source for the try is another peer, link the two, and replace the second
         * with the first.
         *
         * This will remove the subsequent try blocks.
         */
        for (Set<Op03SimpleStatement> peerSet : peerTries.values()) {
            finallyHelper.linkPeerTries(peerSet, allStatements);
            /*
             * Now, unlink (!) the finally block from the remaining try statements in this peer group, as it
             * needs to be lifted to the outer group (we'll add it back in to that).
             */
            finallyHelper.unlinkTries(peerSet);
        }

        /*
         * Find the shortest block set, which is the outer scope, where the finally block needs to go
         */
        List<CompositeBlockIdentifierKey> keys = ListFactory.newList(peerTries.keySet());
        Collections.sort(keys);

        Set<Op03SimpleStatement> tops = peerTries.get(keys.get(0));
        List<Op03SimpleStatement> topTries = Functional.filter(tops, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getStatement() instanceof TryStatement;
            }
        });

        /*
         * If topTries > 1, then we have multiple top tries left over.  It's wierd - abort here.
         */
        if (topTries.size() > 1) return true;

        Op03SimpleStatement topTry = topTries.get(0);
        /*
         * Take the finally catch block, lift it to the level of topTry.  It shouldn't be linked to anything else.
         *
         * We can hack the blocks by removing it from any catch block which isn't in progress already at topTry.
         */
        final Set<BlockIdentifier> retainCatchBlocks = SetFactory.newSet(Functional.filter(topTry.getBlockIdentifiers(), new Predicate<BlockIdentifier>() {
            @Override
            public boolean test(BlockIdentifier in) {
                return in.getBlockType() == BlockType.CATCHBLOCK;
            }
        }));

        List<Op03SimpleStatement> processThese = Functional.filter(allStatements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.getBlockIdentifiers().contains(finallyIdent);
            }
        });
        processThese.add(best);
        for (Op03SimpleStatement finallyMember : processThese) {
            List<BlockIdentifier> retainThese = Functional.filter(best.getBlockIdentifiers(), new Predicate<BlockIdentifier>() {
                @Override
                public boolean test(BlockIdentifier in) {
                    if (in.getBlockType() != BlockType.CATCHBLOCK) return true;
                    return retainCatchBlocks.contains(in);
                }
            });
            retainThese.add(finallyIdent);
            finallyMember.getBlockIdentifiers().retainAll(retainThese);
        }
        best.sources.add(topTry);
        topTry.targets.add(best);

        // If there IS a rethrow, remove it.
        if (orderLast != null) {
            orderLast.replaceStatement(new Nop());
        }

        /*
         * Now... replace the catch expression with a "Finally".
         */
        best.replaceStatement(new FinallyStatement(finallyIdent));

        return true;
    }

    public static boolean verifyPeerSet(Set<Op03SimpleStatement> peers, final FinallyHelper finallyHelper, Set<Op03SimpleStatement> allTries,
                                        FinallyResultAndUsages finallyResultAndUsages) {
        for (Op03SimpleStatement peer : peers) {
            if (!verifyOneTry(peer, finallyHelper, allTries, finallyResultAndUsages)) {
                return false;
            }
        }
        return true;
    }

    /*
     * We need to search this try statement for every exit.  Any non-throwing exit (return / goto / if)
     * which leaves the block has to vector into a block of code which is identical (Barring minor
     * conversions) to finallyStart.
     */
    public static boolean verifyOneTry(Op03SimpleStatement tryStart, final FinallyHelper finallyHelper, final Set<Op03SimpleStatement> allTries,
                                       FinallyResultAndUsages resultAndUsages) {
        TryStatement tryStatement = (TryStatement) (tryStart.getStatement());
        final BlockIdentifier tryBlock = tryStatement.getBlockIdentifier();
        final Set<Op03SimpleStatement> exitTargets = SetFactory.newSet();
        final Map<Op03SimpleStatement, Set<Op03SimpleStatement>> targetCallers = MapFactory.newLazyMap(new UnaryFunction<Op03SimpleStatement, Set<Op03SimpleStatement>>() {
            @Override
            public Set<Op03SimpleStatement> invoke(Op03SimpleStatement arg) {
                return SetFactory.newSet();
            }
        });
        GraphVisitor<Op03SimpleStatement> graphVisitor = new GraphVisitorDFS<Op03SimpleStatement>(tryStart.targets.get(0),
                new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                    @Override
                    public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                        if (arg1.getBlockIdentifiers().contains(tryBlock)) {
                            for (Op03SimpleStatement target : arg1.targets) {
                                arg2.enqueue(target);

                                // We have to check this here, while we still have arg1.
                                if (!target.getBlockIdentifiers().contains(tryBlock)) {
                                    if (!allTries.contains(target)) {
                                        exitTargets.add(target);
                                        targetCallers.get(target).add(arg1);
                                    }
                                }
                            }
                        }
                    }
                });
        graphVisitor.process();
        /* Exit targets are the set of ways off this try block.
         * We now need to verify that each of these exit targets is the same as finallyStart pp.
         *
         * BUT - we also need to verify that we have encountered ALL OF the sources of the exit targets.
         */
        for (Op03SimpleStatement exitTarget : exitTargets) {
            FinallyHelper.Result result = finallyHelper.testEquivalent(exitTarget, tryStatement);
            if (!result.isMatched()) return false;
            resultAndUsages.add(result, targetCallers.get(exitTarget));
        }
        return true;
    }

    public static void identifyFinally(List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory) {
        /* Get all the try statements, get their catches.  For all the EXIT points to the catches, try to identify
         * a common block of code (either before a throw, return or goto.)
         * Be careful, if a finally block contains a throw, this will mess up...
         */
        List<Op03SimpleStatement> tryStarts = Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement tryS : tryStarts) {
            identifyFinally(tryS, in);
        }

    }

    private static boolean verifyLinearBlock(Op03SimpleStatement current, BlockIdentifier block, int num) {
        while (num >= 0) {
            if (num > 0) {
                if (current.targets.size() != 1) return false;
                if (!current.containedInBlocks.contains(block)) return false;
                current = current.targets.get(0);
            } else {
                if (!current.containedInBlocks.contains(block)) return false;
            }
            num--;
        }
        // None of current's targets should be contained in block.
        for (Op03SimpleStatement target : current.targets) {
            if (target.containedInBlocks.contains(block)) return false;
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
        if (!(catchStatementContainer.containedStatement instanceof CatchStatement)) return false;
        CatchStatement catchStatement = (CatchStatement) catchStatementContainer.containedStatement;
        List<ExceptionGroup.Entry> exceptions = catchStatement.getExceptions();
        if (exceptions.size() != 1) return false;
        ExceptionGroup.Entry exception = exceptions.get(0);
        // Exception is *.
        if (!exception.isJustThrowable()) return false;

        // We expect the next 2 and NO more to be in this catch block.
        if (!verifyLinearBlock(start, block, 2)) return false;

        Op03SimpleStatement variableAss = start;
        Op03SimpleStatement monitorExit = start.targets.get(0);
        Op03SimpleStatement rethrow = monitorExit.targets.get(0);

        WildcardMatch wildcardMatch = new WildcardMatch();

        if (!wildcardMatch.match(
                new AssignmentSimple(wildcardMatch.getLValueWildCard("var"), wildcardMatch.getExpressionWildCard("e")),
                variableAss.containedStatement)) {
            return false;
        }

        if (!wildcardMatch.match(
                new MonitorExitStatement(wildcardMatch.getExpressionWildCard("lock")),
                monitorExit.containedStatement)) {
            return false;
        }

        if (!wildcardMatch.match(
                new ThrowStatement(new LValueExpression(wildcardMatch.getLValueWildCard("var"))),
                rethrow.containedStatement)) return false;

        /* This is an artificial catch block - probably.  Remove it, and if we can, remove the associated try
         * statement.
         * (This only makes sense if we eventually replace the MONITOR(ENTER|EXIT) pair with a synchronized
         * block).
         */
        Op03SimpleStatement tryStatementContainer = catchStatementContainer.sources.get(0);
        tryStatementContainer.removeTarget(catchStatementContainer);
        catchStatementContainer.removeSource(tryStatementContainer);
        catchStatementContainer.nopOut();
        variableAss.nopOut();
        monitorExit.nopOut();
        for (Op03SimpleStatement target : rethrow.targets) {
            target.removeSource(rethrow);
            rethrow.removeTarget(target);
        }
        rethrow.nopOut();
        /*
         * Can we remove the try too?
         */
        if (tryStatementContainer.targets.size() == 1) {
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
    public static void removeSynchronizedCatchBlocks(List<Op03SimpleStatement> in) {
        // find all the block statements which are the first statement in a CATCHBLOCK.
        List<Op03SimpleStatement> catchStarts = Functional.filter(in, new FindBlockStarts(BlockType.CATCHBLOCK));
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
        BlockIdentifier blockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.SWITCH);
        // For each of the switch targets, add a 'case' statement
        // We can add them at the end, as long as we've got a post hoc sort.

        // What happens if there's no default statement?  Not sure java permits?
        List<DecodedSwitchEntry> entries = switchData.getJumpTargets();
        InferredJavaType caseType = switchStatement.getSwitchOn().getInferredJavaType();
        Map<InstrIndex, Op03SimpleStatement> firstPrev = MapFactory.newMap();
        for (int x = 0; x < targets.size(); ++x) {
            Op03SimpleStatement target = targets.get(x);
            InstrIndex tindex = target.getIndex();
            if (firstPrev.containsKey(tindex)) {
                target = firstPrev.get(tindex);
            }
            List<Expression> expression = ListFactory.newList();
            if (x != 0) {
                List<Integer> vals = entries.get(x - 1).getValue();
                for (int val : vals) {
                    expression.add(new Literal(TypedLiteral.getInt(val)));
                }
            }
            Op03SimpleStatement caseStatement = new Op03SimpleStatement(target.getBlockIdentifiers(), new CaseStatement(expression, caseType, blockIdentifier, blockIdentifierFactory.getNextBlockIdentifier(BlockType.CASE)), target.getIndex().justBefore());
            // Link casestatement in infront of target - all sources of target should point to casestatement instead, and
            // there should be one link going from caseStatement to target. (it's unambiguous).
            for (Op03SimpleStatement source : target.sources) {
                source.replaceTarget(target, caseStatement);
                caseStatement.addSource(source);
            }
            target.sources.clear();
            target.sources.add(caseStatement);
            caseStatement.addTarget(target);
            in.add(caseStatement);
            firstPrev.put(tindex, caseStatement);
        }
        swatch.replaceStatement(switchStatement.getSwitchStatement(blockIdentifier));
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

            CaseStatement caseStatement = (CaseStatement) thisCase.containedStatement;
            BlockIdentifier caseBlock = caseStatement.getCaseBlock();

            int indexLastInThis = getFarthestReachableInRange(statements, indexThisCase, indexNextCase);
            if (indexLastInThis != indexNextCase - 1) {
                throw new ConfusedCFRException("Case statement doesn't cover expected range.");
            }
            indexLastInLastBlock = indexLastInThis;
            for (int y = indexThisCase + 1; y <= indexLastInThis; ++y) {
                Op03SimpleStatement statement = statements.get(y);
                statement.markBlock(caseBlock);
                if (statement.getJumpType().isUnknown()) {
                    for (Op03SimpleStatement innerTarget : statement.targets) {
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
            List<Op03SimpleStatement> lstFwdTargets = ListFactory.newList(forwardTargets);
            Collections.sort(lstFwdTargets, new CompareByIndex());
            Op03SimpleStatement afterCaseGuess = lstFwdTargets.get(0);
            int indexAfterCase = statements.indexOf(afterCaseGuess);

            CaseStatement caseStatement = (CaseStatement) lastCase.containedStatement;
            BlockIdentifier caseBlock = caseStatement.getCaseBlock();

            int indexLastInThis = getFarthestReachableInRange(statements, indexLastCase, indexAfterCase);
            if (indexLastInThis != indexAfterCase - 1) {
                throw new ConfusedCFRException("Final statement in case doesn't meet smallest exit.");
            }
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
            if (breakSource.getJumpType().isUnknown()) {
                ((JumpingStatement) breakSource.containedStatement).setJumpType(JumpType.BREAK);
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
    private static void rewriteArrayForLoop(final Op03SimpleStatement loop, List<Op03SimpleStatement> statements) {

        /*
         * loop should have one back-parent.
         */
        Op03SimpleStatement preceeding = findSingleBackSource(loop);
        if (preceeding == null) return;

        ForStatement forStatement = (ForStatement) loop.containedStatement;

        WildcardMatch wildcardMatch = new WildcardMatch();

        if (!wildcardMatch.match(
                new AssignmentSimple(wildcardMatch.getLValueWildCard("iter"), new Literal(TypedLiteral.getInt(0))),
                forStatement.getInitial())) return;

        LValue originalLoopVariable = wildcardMatch.getLValueWildCard("iter").getMatch();

        // Assignments are fiddly, as they can be assignmentPreChange or regular Assignment.
        AbstractAssignmentExpression assignment = forStatement.getAssignment();
        boolean incrMatch = assignment.isSelfMutatingOp1(originalLoopVariable, ArithOp.PLUS);
        if (!incrMatch) return;

        if (!wildcardMatch.match(
                new ComparisonOperation(
                        new LValueExpression(originalLoopVariable),
                        new LValueExpression(wildcardMatch.getLValueWildCard("bound")),
                        CompOp.LT), forStatement.getCondition())) return;

        LValue originalLoopBound = wildcardMatch.getLValueWildCard("bound").getMatch();

        // Bound should have been constructed RECENTLY, and should be an array length.
        // TODO: Let's just check the single backref from the for loop test.
        if (!wildcardMatch.match(
                new AssignmentSimple(originalLoopBound, new ArrayLength(new LValueExpression(wildcardMatch.getLValueWildCard("array")))),
                preceeding.containedStatement)) return;

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
        if (!wildcardMatch.match(
                new AssignmentSimple(wildcardMatch.getLValueWildCard("sugariter"),
                        new ArrayIndex(new LValueExpression(originalArray), new LValueExpression(originalLoopVariable))),
                loopStart.containedStatement)) return;

        LValue sugarIter = wildcardMatch.getLValueWildCard("sugariter").getMatch();

        // It's probably valid.  We just have to make sure that array and index aren't assigned to anywhere in the loop
        // body.
        final BlockIdentifier forBlock = forStatement.getBlockIdentifier();
        List<Op03SimpleStatement> statementsInBlock = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return in.containedInBlocks.contains(forBlock);
            }
        });
//
//        for (Op03SimpleStatement inBlock : statementsInBlock) {
//            if (inBlock == loopStart) continue;
//            Statement inStatement = inBlock.containedStatement;
//            LValue updated = inStatement.getCreatedLValue();
//            if (updated == null) continue;
//            if (updated.equals(sugarIter) || updated.equals(originalArray)) {
//                return;
//            }
//        }

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
                    return;
                }
            }
            LValue updated = inStatement.getCreatedLValue();
            if (updated == null) continue;
            if (cantUpdate.contains(updated)) {
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
            return;
        }


        loop.replaceStatement(new ForIterStatement(forBlock, sugarIter, arrayStatement));
        loopStart.nopOut();
        preceeding.nopOut();
        if (prepreceeding != null) {
            prepreceeding.nopOut();
        }
    }

    public static void rewriteArrayForLoops(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> loops = Functional.filter(statements, new TypeFilter<ForStatement>(ForStatement.class));
        for (Op03SimpleStatement loop : loops) {
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
        if (wildcardMatch.match(
                new AssignmentSimple(wildcardMatch.getLValueWildCard("sugariter"),
                        wildcardMatch.getMemberFunction("nextfn", "next", new LValueExpression(wildcardMatch.getLValueWildCard("iterable")))),
                loopStart.containedStatement)) {
        } else if (wildcardMatch.match(
                new AssignmentSimple(wildcardMatch.getLValueWildCard("sugariter"),
                        wildcardMatch.getCastExpressionWildcard("cast", wildcardMatch.getMemberFunction("nextfn", "next", new LValueExpression(wildcardMatch.getLValueWildCard("iterable"))))),
                loopStart.containedStatement)) {
            // It's a cast expression - so we know that there's a type we might be able to push back up.
            isCastExpression = true;
        } else {
            return;
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
        loopStart.nopOut();
        preceeding.nopOut();
    }

    public static void rewriteIteratorWhileLoops(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> loops = Functional.filter(statements, new TypeFilter<WhileStatement>(WhileStatement.class));
        for (Op03SimpleStatement loop : loops) {
            rewriteIteratorWhileLoop(loop, statements);
        }
    }

    public static void findSynchronizedStart(final Op03SimpleStatement start, final Expression monitor) {
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
        GraphVisitor<Op03SimpleStatement> marker = new GraphVisitorDFS<Op03SimpleStatement>(start,
                new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
                    @Override
                    public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                        Statement statement = arg1.containedStatement;
                        if (statement instanceof MonitorExitStatement) {
                            if (monitor.equals(((MonitorExitStatement) statement).getMonitor())) {
                                foundExits.add(arg1);
                                /*
                                 * If there's a return / throw / goto immediately after this, then we know that the brace
                                 * is validly moved.
                                 */
                                if (arg1.targets.size() == 1) {
                                    Op03SimpleStatement target = arg1.targets.get(0);
                                    Statement targetStatement = target.containedStatement;
                                    if (targetStatement instanceof ReturnStatement ||
                                            targetStatement instanceof ThrowStatement ||
                                            targetStatement instanceof GotoStatement) {
                                        extraNodes.add(target);
                                    }
                                }
                                return;
                            }
                        }
                        addToBlock.add(arg1);
                        for (Op03SimpleStatement target : arg1.getTargets()) {
                            arg2.enqueue(target);
                        }
                    }
                }
        );
        marker.process();

        MonitorEnterStatement monitorEnterStatement = (MonitorEnterStatement) (start.containedStatement);
        BlockIdentifier blockIdentifier = monitorEnterStatement.getBlockIdentifier();
        for (Op03SimpleStatement contained : addToBlock) {
            if (contained != start) {
                contained.containedInBlocks.add(blockIdentifier);
            }
        }

        for (Op03SimpleStatement exit : foundExits) {
            exit.nopOut();
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
        List<Op03SimpleStatement> exits = Functional.filter(statements, new TypeFilter<MonitorEnterStatement>(MonitorEnterStatement.class));
        // Each exit can be tied to one enter, which is the first one found by
        // walking code backwards and not passing any other exit/enter for this var.
        // (Every exit from a synchronised block has to exit, so if there's any possibiliy of an exception... )

        for (Op03SimpleStatement exit : exits) {
            MonitorEnterStatement monitorExitStatement = (MonitorEnterStatement) exit.containedStatement;

            findSynchronizedStart(exit, monitorExitStatement.getMonitor());
        }
    }

    private static void removePointlessSwitchDefault(Op03SimpleStatement swtch) {
        SwitchStatement switchStatement = (SwitchStatement) swtch.getStatement();
        BlockIdentifier switchBlock = switchStatement.getSwitchBlock();
        // If one of the targets is a "default", and it's definitely a target for this switch statement
        // AND it hasn't been marked as belonging to the block, remove it.
        // A default with no code is of course equivalent to no default.
        for (Op03SimpleStatement tgt : swtch.getTargets()) {
            Statement statement = tgt.getStatement();
            if (statement instanceof CaseStatement) {
                CaseStatement caseStatement = (CaseStatement) statement;
                if (caseStatement.getSwitchBlock() == switchBlock) {
                    if (caseStatement.isDefault()) {
                        if (tgt.containedInBlocks.contains(switchBlock)) {
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
        if (!(arrayLValue instanceof StackSSALabel)) {
            return false;
        }
        StackSSALabel array = (StackSSALabel) arrayLValue;
        AbstractNewArray arrayDef = start.getNewArrayWildCard("def").getMatch();
        Expression dimSize0 = arrayDef.getDimSize(0);
        if (!(dimSize0 instanceof Literal)) return false;
        Literal lit = (Literal) dimSize0;
        if (lit.getValue().getType() != TypedLiteral.LiteralType.Integer) return false;
        int bound = (Integer) lit.getValue().getValue();

        Op03SimpleStatement next = newArray;
        List<Expression> anon = ListFactory.newList();
        List<Op03SimpleStatement> anonAssigns = ListFactory.newList();
        for (int x = 0; x < bound; ++x) {
            if (next.targets.size() != 1) {
                return false;
            }
            next = next.targets.get(0);
            WildcardMatch testAnon = new WildcardMatch();
            Literal idx = new Literal(TypedLiteral.getInt(x));
            if (!testAnon.match(
                    new AssignmentSimple(
                            new ArrayVariable(new ArrayIndex(new StackValue(array), idx)),
                            testAnon.getExpressionWildCard("val")),
                    next.containedStatement)) {
                return false;
            }
            anon.add(testAnon.getExpressionWildCard("val").getMatch());
            anonAssigns.add(next);
        }
        AssignmentSimple replacement = new AssignmentSimple(arrayLValue.getInferredJavaType(), assignmentSimple.getCreatedLValue(), new NewAnonymousArray(arrayDef.getInferredJavaType(), arrayDef.getNumDims(), anon, false));
        newArray.replaceStatement(replacement);
        StackEntry arrayStackEntry = array.getStackEntry();
        for (Op03SimpleStatement create : anonAssigns) {
            arrayStackEntry.decrementUsage();
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

    @Override
    public String toString() {
        return "Op03SimpleStatement - " + index + " : " + containedStatement;
    }
}
