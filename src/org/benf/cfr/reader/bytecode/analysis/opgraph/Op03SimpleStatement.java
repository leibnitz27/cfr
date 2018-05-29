package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.AnonymousClassUsage;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.NarrowingTypeRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.*;
import org.benf.cfr.reader.bytecode.analysis.parse.*;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheckImpl;
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
import java.util.logging.Logger;

public class Op03SimpleStatement implements MutableGraph<Op03SimpleStatement>, Dumpable, StatementContainer<Statement>, IndexedStatement {
    private static final Logger logger = LoggerFactory.create(Op03SimpleStatement.class);

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
    public void copyBlockInformationFrom(StatementContainer other) {
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

    public void removeGotoTarget(Op03SimpleStatement oldTarget) {
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
        if (this.firstStatementInThisBlock != null && this.firstStatementInThisBlock != blockIdentifier) {
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

    private void findCreation(CreationCollector creationCollector) {
        containedStatement.collectObjectCreation(creationCollector);
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



    private void collectLocallyMutatedVariables(SSAIdentifierFactory<LValue> ssaIdentifierFactory) {
        this.ssaIdentifiers = containedStatement.collectLocallyMutatedVariables(ssaIdentifierFactory);
    }

    public void forceSSAIdentifiers(SSAIdentifiers<LValue> newIdentifiers) {
        this.ssaIdentifiers = newIdentifiers;
    }


    /*
     * FIXME - the problem here is that LValues COULD be mutable.  FieldValue /is/ mutable.
     *
     * Therefore we can't keep it as a key!!!!!!
     */
    public static void assignSSAIdentifiers(Method method, List<Op03SimpleStatement> statements) {

        SSAIdentifierFactory<LValue> ssaIdentifierFactory = new SSAIdentifierFactory<LValue>(null);

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


        Expression newRhs = null;
        if (r1 instanceof ArithmeticOperation && ((ArithmeticOperation) r1).isMutationOf(l1)) {
            ArithmeticOperation ar1 = (ArithmeticOperation) r1;
            AbstractMutatingAssignmentExpression me = ar1.getMutationOf(l1);
            newRhs = me;
        }

        if (newRhs == null) newRhs = new AssignmentExpression(l1, r1);
        /*
         * But only if we have enough type information to know this is ok.
         */
        if (newRhs.getInferredJavaType().getJavaTypeInstance() != l2.getInferredJavaType().getJavaTypeInstance()) {
            return;
        }

        stm2.replaceStatement(new AssignmentSimple(l2, newRhs));
        stm1.nopOut();
    }



    /*
     * vX = ?
     * ? = vX + 1
     *
     * -->
     *
     * vX = ?++
     */
    private static void replacePostChangeAssignment(Op03SimpleStatement statement) {
        AssignmentSimple assignmentSimple = (AssignmentSimple) statement.containedStatement;
        LValue postIncLValue = assignmentSimple.getCreatedLValue();

        if (statement.sources.size() != 1) return;

        Op03SimpleStatement prior = statement.sources.get(0);
        Statement statementPrior = prior.getStatement();
        if (!(statementPrior instanceof AssignmentSimple)) return;

        AssignmentSimple assignmentSimplePrior = (AssignmentSimple) statementPrior;
        LValue tmp = assignmentSimplePrior.getCreatedLValue();
        if (!(tmp instanceof StackSSALabel)) return;

        if (!assignmentSimplePrior.getRValue().equals(new LValueExpression(postIncLValue))) return;

        StackSSALabel tmpStackVar = (StackSSALabel) tmp;
        Expression stackValue = new StackValue(tmpStackVar);
        Expression incrRValue = assignmentSimple.getRValue();

        if (!(incrRValue instanceof ArithmeticOperation)) return;
        ArithmeticOperation arithOp = (ArithmeticOperation) incrRValue;
        ArithOp op = arithOp.getOp();
        if (!(op.equals(ArithOp.PLUS) || op.equals(ArithOp.MINUS))) return;

        Expression lhs = arithOp.getLhs();
        Expression rhs = arithOp.getRhs();
        if (stackValue.equals(lhs)) {
            if (!Literal.equalsAnyOne(rhs)) return;
        } else if (stackValue.equals(rhs)) {
            if (!Literal.equalsAnyOne(lhs)) return;
            if (op.equals(ArithOp.MINUS)) return;
        } else {
            return;
        }

        ArithmeticPostMutationOperation postMutationOperation = new ArithmeticPostMutationOperation(postIncLValue, op);
        prior.nopOut();
        statement.replaceStatement(new AssignmentSimple(tmp, postMutationOperation));
    }

    /* We're searching for something a bit too fiddly to use wildcards on,
     * so lots of test casting :(
     */
    private static boolean replacePreChangeAssignment(Op03SimpleStatement statement) {
        AssignmentSimple assignmentSimple = (AssignmentSimple) statement.containedStatement;

        LValue lValue = assignmentSimple.getCreatedLValue();

        // Is it an arithop
        Expression rValue = assignmentSimple.getRValue();
        if (!(rValue instanceof ArithmeticOperation)) return false;

        // Which is a mutation
        ArithmeticOperation arithmeticOperation = (ArithmeticOperation) rValue;
        if (!arithmeticOperation.isMutationOf(lValue)) return false;

        // Create an assignment prechange with the mutation
        AbstractMutatingAssignmentExpression mutationOperation = arithmeticOperation.getMutationOf(lValue);

        AssignmentPreMutation res = new AssignmentPreMutation(lValue, mutationOperation);
        statement.replaceStatement(res);
        return true;
    }

    public static void replacePrePostChangeAssignments(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> assignments = Functional.filter(statements, new TypeFilter<AssignmentSimple>(AssignmentSimple.class));
        for (Op03SimpleStatement assignment : assignments) {
            if (replacePreChangeAssignment(assignment)) continue;
            replacePostChangeAssignment(assignment);
        }
    }

    private static boolean eliminateCatchTemporary(Op03SimpleStatement catchh) {
        if (catchh.targets.size() != 1) return false;
        Op03SimpleStatement maybeAssign = catchh.targets.get(0);

        CatchStatement catchStatement = (CatchStatement) catchh.getStatement();
        LValue catching = catchStatement.getCreatedLValue();

        if (!(catching instanceof StackSSALabel)) return false;
        StackSSALabel catchingSSA = (StackSSALabel) catching;
        if (catchingSSA.getStackEntry().getUsageCount() != 1) return false;

        while (maybeAssign.getStatement() instanceof TryStatement) {
            // Note that the 'tried' path is always path 0 of a try statement.
            maybeAssign = maybeAssign.targets.get(0);
        }
        WildcardMatch match = new WildcardMatch();
        if (!match.match(new AssignmentSimple(match.getLValueWildCard("caught"), new StackValue(catchingSSA)),
                maybeAssign.getStatement())) {
            return false;
        }

        // Hurrah - maybeAssign is an assignment of the caught value.
        catchh.replaceStatement(new CatchStatement(catchStatement.getExceptions(), match.getLValueWildCard("caught").getMatch()));
        maybeAssign.nopOut();
        return true;
    }

    public static List<Op03SimpleStatement> eliminateCatchTemporaries(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> catches = Functional.filter(statements, new TypeFilter<CatchStatement>(CatchStatement.class));
        boolean effect = false;
        for (Op03SimpleStatement catchh : catches) {
            effect = effect | eliminateCatchTemporary(catchh);
        }
        if (effect) {
            // Before we identify finally, clean the code again.
            statements = Cleaner.removeUnreachableCode(statements, false);
        }
        return statements;
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

    private static class UsageWatcher extends AbstractExpressionRewriter {
        private final LValue needle;
        boolean found = false;

        private UsageWatcher(LValue needle) {
            this.needle = needle;
        }

        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (needle.equals(lValue)) found = true;
            return super.rewriteExpression(lValue, ssaIdentifiers, statementContainer, flags);
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
                    /*
                     * Verify that the saident of tgt does not change.
                     */
                    SSAIdentifiers preChangeIdents = preChange.getSSAIdentifiers();
                    SSAIdentifiers assignIdents = current.getSSAIdentifiers();
                    if (!preChangeIdents.isValidReplacement(tgt, assignIdents)) {
                        return;
                    }
                    assignIdents.setKnownIdentifierOnExit(mutatedLValue, preChangeIdents.getSSAIdentOnExit(mutatedLValue));
                    current.replaceStatement(new AssignmentSimple(tgt, mutation.getPostMutation()));
                    preChange.nopOut();
                    return;
                }
            }
            current.rewrite(usageWatcher);
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
        if (assignments.isEmpty()) return;

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
    public static void condenseConstruction(DCCommonState state, Method method, List<Op03SimpleStatement> statements, AnonymousClassUsage anonymousClassUsage) {
        CreationCollector creationCollector = new CreationCollector(anonymousClassUsage);
        for (Op03SimpleStatement statement : statements) {
            statement.findCreation(creationCollector);
        }
        creationCollector.condenseConstructions(method, state);
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
     *
     * TODO :
     * /Users/lee/Downloads/jarsrc/com/strobel/decompiler/languages/java/utilities/RedundantCastUtility.class :: processCall.
     *
     * has
     * if (c1) then a
     * if (c2) then b
     * goto a
     *
     * ==>
     *
     * if (c1) then a
     * if (!c2) then a
     * goto b
     *
     * ==>
     *
     * if (c1 || !c2) then a
     * goto b
     *
     * also
     * /Users/lee/code/java/cfr_tests/out/production/cfr_tests/org/benf/cfr/tests/ShortCircuitAssignTest7.class
     */
    public static boolean condenseConditionals(List<Op03SimpleStatement> statements) {
        boolean effect = false;
        for (int x = 0; x < statements.size(); ++x) {
            boolean retry = false;
            do {
                retry = false;
                Op03SimpleStatement op03SimpleStatement = statements.get(x);
                // If successful, this statement will be nopped out, and the next one will be
                // the combination of the two.
                Statement inner = op03SimpleStatement.getStatement();
                if (!(inner instanceof IfStatement)) continue;
                Op03SimpleStatement fallThrough = op03SimpleStatement.getTargets().get(0);
                Op03SimpleStatement taken = op03SimpleStatement.getTargets().get(1);
                Statement fallthroughInner = fallThrough.getStatement();
                Statement takenInner = taken.getStatement();
                // Is the taken path just jumping straight over the non taken?
                boolean takenJumpBy1 = (x < statements.size() - 2) && statements.get(x+2) == taken;

                if (fallthroughInner instanceof IfStatement) {
                    Op03SimpleStatement sndIf = fallThrough;
                    Op03SimpleStatement sndTaken = sndIf.getTargets().get(1);
                    Op03SimpleStatement sndFallThrough = sndIf.getTargets().get(0);

                    retry = condenseIfs(op03SimpleStatement, sndIf, taken, sndTaken, sndFallThrough, false);

//                    if (if2.condenseWithPriorIfStatement(if1, false)) {
//                        retry = true;
//                    }
                } else if (fallthroughInner.getClass() == GotoStatement.class && takenJumpBy1 && takenInner instanceof IfStatement) {
                    // If it's not an if statement, we might have to negate a jump - i.e.
                    // if (c1) a1
                    // goto a2
                    // a1 : if (c2) goto a2
                    // a3
                    //
                    // is (of course) equivalent to
                    // if (!c1 || c2) goto a2

                    Op03SimpleStatement negatedTaken = fallThrough.getTargets().get(0);
                    Op03SimpleStatement sndIf = statements.get(x+2);
                    Op03SimpleStatement sndTaken = sndIf.getTargets().get(1);
                    Op03SimpleStatement sndFallThrough = sndIf.getTargets().get(0);

                    retry = condenseIfs(op03SimpleStatement, sndIf, negatedTaken, sndTaken, sndFallThrough, true);

//                    IfStatement if1 = (IfStatement)inner;
//                    IfStatement if2 = (IfStatement)takenInner;
//                    if (if2.condenseWithPriorIfStatement(if1, true)) {
//                        retry = true;
//                    }
                }

                if (retry) {
                    effect = true;
                    do {
                        x--;
                    } while (statements.get(x).isAgreedNop() && x > 0);
                }
            } while (retry);
        }
        return effect;
    }

    // if (c1) goto a
    // if (c2) goto b
    // a    (equivalently, GOTO a)
    // ->
    // if (!c1 && c2) goto b

    // if (c1) goto a
    // if (c2) goto a
    // b
    // ->
    // if (c1 || c2) goto a
    private static boolean condenseIfs(Op03SimpleStatement if1, Op03SimpleStatement if2,
                                       Op03SimpleStatement taken1, Op03SimpleStatement taken2, Op03SimpleStatement fall2,
                                       boolean negated1) {
        if (if2.sources.size() != 1) {
            return false;
        }

        BoolOp resOp;
        boolean negate1;

        if (taken1 == fall2) {
            resOp = BoolOp.AND;
            negate1 = true;
        } else if (taken1 == taken2) {
            resOp = BoolOp.OR;
            negate1 = false;
        } else {
            Statement fall2stm = fall2.getStatement();
            if (fall2stm.getClass() == GotoStatement.class && fall2.getTargets().get(0) == taken1) {
                resOp = BoolOp.AND;
                negate1 = true;
            } else {
                return false;
            }
        }

        ConditionalExpression cond1 = ((IfStatement)if1.getStatement()).getCondition();
        ConditionalExpression cond2 = ((IfStatement)if2.getStatement()).getCondition();
        if (negated1) {
            negate1 = !negate1;
        }
        if (negate1) cond1 = cond1.getNegated();
        ConditionalExpression combined = new BooleanOperation(cond1, cond2, resOp);
        combined = combined.simplify();
        // We need to remove both the targets from the first if, all the sources from the second if (which should be just 1!).
        // Then first if becomes a NOP which points directly to second, and second gets new condition.
        if2.replaceStatement(new IfStatement(combined));

        // HACK - we know this is how nopoutconditional will work.
        for (Op03SimpleStatement target1 : if1.getTargets()){
            target1.removeSource(if1);
        }
        if1.targets.clear();
        for (Op03SimpleStatement source1 : if2.getSources()) {
            source1.removeGotoTarget(if2);
        }
        if2.sources.clear();
        if1.targets.add(if2);
        if2.sources.add(if1);

        if1.nopOutConditional();

        return true;
    }

    public static void simplifyConditionals(List<Op03SimpleStatement> statements, boolean aggressive) {
        for (Op03SimpleStatement statement : statements) {
            statement.simplifyConditional();
        }

        // Fixme - surely simplifyConditional above should be in the rewriter!?
        if (aggressive) {
            ExpressionRewriter conditionalSimplifier = new ConditionalSimplifyingRewriter();
            for (Op03SimpleStatement statement : statements) {
                statement.rewrite(conditionalSimplifier);
            }
        }
    }


    public static boolean condenseConditionals2(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> ifStatements = Functional.filter(statements, new TypeFilter<IfStatement>(IfStatement.class));
        boolean result = false;
        for (Op03SimpleStatement ifStatement : ifStatements) {
            // separated for stepping
            if (condenseConditional2_type1(ifStatement, statements)) {
                result = true;
            } else if (condenseConditional2_type2(ifStatement, statements)) {
                result = true;
            } else if (condenseConditional2_type3(ifStatement, statements)) {
                result = true;
            }
        }
        return result;
    }

    /* Search for
     * stackvar = X
     * Y = stackvar
     *
     * convert to stackvar = Y = X
     *
     * Otherwise this gets in the way of rolling assignments into conditionals.
     */
    private static boolean normalizeDupAssigns_type1(Op03SimpleStatement stm) {
        Statement inner1 = stm.getStatement();
        if (!(inner1 instanceof AssignmentSimple)) return false;
        List<Op03SimpleStatement> tgts = stm.getTargets();
        if (tgts.size() != 1) return false;
        Op03SimpleStatement next = tgts.get(0);
        Statement inner2 = next.getStatement();
        if (!(inner2 instanceof AssignmentSimple)) return false;

        if (next.getTargets().size() != 1) return false;
        Op03SimpleStatement after = next.getTargets().get(0);
        if (!(after.getStatement() instanceof IfStatement)) return false;

        AssignmentSimple a1 = (AssignmentSimple)inner1;
        AssignmentSimple a2 = (AssignmentSimple)inner2;

        LValue l1 = a1.getCreatedLValue();
        LValue l2 = a2.getCreatedLValue();
        Expression r1 = a1.getRValue();
        Expression r2 = a2.getRValue();

        if (!(r2 instanceof StackValue)) return false;
        StackSSALabel s2 = ((StackValue)r2).getStackValue();
        if (!l1.equals(s2)) return false;
        next.nopOut();
        // And copy ssa identifiers from next.
        stm.ssaIdentifiers = next.ssaIdentifiers;
        stm.replaceStatement(new AssignmentSimple(l1, new AssignmentExpression(l2, r1)));
        return true;
    }

    public static boolean normalizeDupAssigns(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> assignStatements = Functional.filter(statements, new TypeFilter<AssignmentSimple>(AssignmentSimple.class));
        boolean result = false;
        for (Op03SimpleStatement assign : assignStatements) {
            if (normalizeDupAssigns_type1(assign)) {
                result = true;
            }
        }
        return result;
    }

    private static void replaceReturningIf(Op03SimpleStatement ifStatement, boolean aggressive) {
        if (!(ifStatement.containedStatement.getClass() == IfStatement.class)) return;
        IfStatement innerIf = (IfStatement) ifStatement.containedStatement;
        Op03SimpleStatement tgt = ifStatement.getTargets().get(1);
        final Op03SimpleStatement origtgt = tgt;
        boolean requireJustOneSource = !aggressive;
        do {
            Op03SimpleStatement next = Misc.followNopGoto(tgt, requireJustOneSource, aggressive);
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
            Op03SimpleStatement next = Misc.followNopGoto(tgt, requireJustOneSource, aggressive);
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

    private static boolean pushReturnBack(Method method, final Op03SimpleStatement returnStm) {

        ReturnStatement returnStatement = (ReturnStatement) returnStm.getStatement();
        final List<Op03SimpleStatement> replaceWithReturn = ListFactory.newList();

        new GraphVisitorDFS<Op03SimpleStatement>(returnStm.getSources(), new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
            @Override
            public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                Class<?> clazz = arg1.getStatement().getClass();
                if (clazz == CommentStatement.class ||
                    clazz == Nop.class ||
                    clazz == DoStatement.class) {
                    arg2.enqueue(arg1.getSources());
                } else if (clazz == WhileStatement.class) {
                    // only if it's 'while true'.
                    WhileStatement whileStatement = (WhileStatement)arg1.getStatement();
                    if (whileStatement.getCondition() == null) {
                        arg2.enqueue(arg1.getSources());
                        replaceWithReturn.add(arg1);
                    }
                } else if (clazz == GotoStatement.class) {
                    arg2.enqueue(arg1.getSources());
                    replaceWithReturn.add(arg1);
                }
            }
        }).process();

        if (replaceWithReturn.isEmpty()) return false;

        CloneHelper cloneHelper = new CloneHelper();
        for (Op03SimpleStatement remove : replaceWithReturn) {
            remove.replaceStatement(returnStatement.deepClone(cloneHelper));
            for (Op03SimpleStatement tgt : remove.getTargets()) {
                tgt.removeSource(remove);
            }
            remove.targets.clear();
        }

        return true;
    }

    /*

    (b? c : a) || (c ? a : b)
    proves remarkably painful to structure.
    CondTest5*
        
   S1  if (A) GOTO S4
   S2  if (B) GOTO S5
   S3  GOTO Y
   S4  if (C) goto Y
   S5  
         -->

   S1 if (A ? C : !B) GOTO Y
   S2 GOTO S5
   S3 NOP // unjoined
   S4 NOP // unjoined

     */
    private static boolean condenseConditional2_type3(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> allStatements) {
        Op03SimpleStatement s1c = ifStatement;
        Statement s1 = s1c.containedStatement;
        if (s1.getClass() != IfStatement.class) return false;
        Op03SimpleStatement s4c = ifStatement.targets.get(1);
        Op03SimpleStatement s2c = ifStatement.targets.get(0);
        Statement s2 = s2c.getStatement();
        if (s2.getClass() != IfStatement.class) return false;
        Statement s4 = s4c.getStatement();
        if (s4.getClass() != IfStatement.class) return false;
        Op03SimpleStatement s3c = s2c.targets.get(0);
        Statement s3 = s3c.getStatement();
        if (s3.getClass() != GotoStatement.class) return false;

        Op03SimpleStatement s5c = s2c.targets.get(1);
        Op03SimpleStatement y = s3c.targets.get(0);
        if (s4c.targets.get(1) != y) return false;
        if (s4c.targets.get(0) != s5c) return false;
        if (s2c.sources.size() != 1) return false;
        if (s3c.sources.size() != 1) return false;
        if (s4c.sources.size() != 1) return false;
        IfStatement is1 = (IfStatement)s1;
        IfStatement is2 = (IfStatement)s2;
        IfStatement is4 = (IfStatement)s4;
        ConditionalExpression cond = new BooleanExpression(new TernaryExpression(is1.getCondition(), is4.getCondition(), is2.getCondition().getNegated()));

        s1c.replaceStatement(new IfStatement(cond));
        s1c.replaceTarget(s4c,y);
        y.replaceSource(s4c, s1c);

        // Fix sources / targets.
        s2c.replaceStatement(new GotoStatement());
        s2c.removeGotoTarget(s3c);
        s3c.removeSource(s2c);
        s3c.clear();
        s4c.clear();

        // If we know for a fact that the original nodes were laid out linearly, then we can assume fallthrough from
        // S1 to S5.
        // This violates the "next is fallthrough" invariant temporarily, and is only ok because we KNOW
        // we will tidy up immediately.
        int idx = allStatements.indexOf(s1c);
        if (allStatements.size() > idx+5
            && allStatements.get(idx+1) == s2c
            && allStatements.get(idx+2) == s3c
            && allStatements.get(idx+3) == s4c
            && allStatements.get(idx+4) == s5c) {
            s5c.replaceSource(s2c, s1c);
            s1c.replaceTarget(s2c, s5c);
            s2c.clear();
        }

        return true;
    }

    /*
     * Attempt to find really simple inline ternaries / negations, so we can convert them before conditional rollup.
     */
    private static boolean condenseConditional2_type2(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> allStatements) {
        Statement innerStatement = ifStatement.getStatement();
        if (!(innerStatement instanceof IfStatement)) return false;
        IfStatement innerIf = (IfStatement)innerStatement;
        Op03SimpleStatement tgt1 = ifStatement.targets.get(0);
        final Op03SimpleStatement tgt2 = ifStatement.targets.get(1);
        if (tgt1.sources.size() != 1) return false;
        if (tgt2.sources.size() != 1) return false;
        if (tgt1.targets.size() != 1) return false;
        if (tgt2.targets.size() != 1) return false;
        Op03SimpleStatement evTgt = tgt1.targets.get(0);
        evTgt = Misc.followNopGoto(evTgt, true, false);
        Op03SimpleStatement oneSource = tgt1;
        if (!(evTgt.sources.contains(oneSource) || evTgt.sources.contains(oneSource = oneSource.targets.get(0)))) {
            return false;
        }
        if (evTgt.sources.size() < 2) return false; // FIXME.  Shouldnt' clear, below.
        if (tgt2.targets.get(0) != evTgt) return false; // asserted tgt2 is a source of evTgt.
        Statement stm1 = tgt1.getStatement();
        Statement stm2 = tgt2.getStatement();
        if (!(stm1 instanceof AssignmentSimple && stm2 instanceof AssignmentSimple)) {
            return false;
        }
        AssignmentSimple a1 = (AssignmentSimple)stm1;
        AssignmentSimple a2 = (AssignmentSimple)stm2;
        LValue lv = a1.getCreatedLValue();
        if (!lv.equals(a2.getCreatedLValue())) return false;
        ConditionalExpression condition = innerIf.getCondition().getNegated();
        condition = condition.simplify();
        ifStatement.replaceStatement(new AssignmentSimple(lv, new TernaryExpression(condition, a1.getRValue(), a2.getRValue())));
        oneSource.replaceStatement(new Nop());
        oneSource.removeTarget(evTgt);
        tgt2.replaceStatement(new Nop());
        tgt2.removeTarget(evTgt);
        evTgt.removeSource(oneSource);
        evTgt.removeSource(tgt2);
        evTgt.sources.add(ifStatement);
        for (Op03SimpleStatement tgt : ifStatement.targets) {
            tgt.removeSource(ifStatement);
        }
        ifStatement.targets.clear();
        ifStatement.addTarget(evTgt);
        tgt1.replaceStatement(new Nop());
        // Reduce count, or lvalue condensing won't work.
        if (lv instanceof StackSSALabel) {
            ((StackSSALabel) lv).getStackEntry().decSourceCount();
        }
        return true;
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
        nottaken2 = Misc.followNopGotoChain(nottaken2, true, false);
        do {
            Op03SimpleStatement nontaken2rewrite = Misc.followNopGoto(nottaken2, true, false);
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
            Op03SimpleStatement nontaken3rewrite = Misc.followNopGoto(nottaken3, true, false);
            if (nontaken3rewrite == nottaken3) break;
            notTaken3Source = nottaken3;
            nottaken3 = nontaken3rewrite;
        } while (true);

        // nottaken2 = nottaken3 = c
        if (nottaken2 != nottaken3) {
            // There's one final thing we can get away with - if these are both returns, and they are IDENTICAL
            // (i.e. same, AND for any variables accessed, ssa-same), then we can assume nottaken2 is a rewritten branch
            // to nottaken3.
            if (nottaken2.getStatement() instanceof ReturnStatement) {
                if (!nottaken2.getStatement().equivalentUnder(nottaken3.getStatement(), new StatementEquivalenceConstraint(nottaken2, nottaken3))) {
                    return false;
                }
            } else {
                return false;
            }
        }
        if (taken2 != taken3) return false; // taken2 = taken3 = b;
        /*
         * rewrite as if ((w && z) || x)
         *
         *
         */
        IfStatement if1 = (IfStatement) ifStatement.containedStatement;
        IfStatement if2 = (IfStatement) ifStatement2.containedStatement;
        IfStatement if3 = (IfStatement) ifStatement3.containedStatement;

        ConditionalExpression newCond = new BooleanExpression(
                new TernaryExpression(
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
                    break;
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
            if (lValue instanceof StackSSALabel) return;
            // We don't have to worry about RHS having undesired side effects if we roll it into the
            // conditional - that has already happened.
            LValueUsageCollectorSimple lvc = new LValueUsageCollectorSimple();
            // NB - this will collect values even if they are NOT guaranteed to be used
            // i.e. are on the RHS of a comparison, or in a ternary.
            conditionalExpression.collectUsedLValues(lvc);
            if (!lvc.isUsed(lValue)) return;
            AbstractAssignment assignment = (AbstractAssignment) (source.containedStatement);

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
            SSAIdentifiers<LValue> beforeSSA = source.getSSAIdentifiers();
            SSAIdentifiers<LValue> afterSSA = ifStatement.getSSAIdentifiers();

            Set<LValue> intersection  = SetUtil.intersectionOrNull(used, usedComparison);
            if (intersection != null) {
                // If there's an intersection, we require the ssa idents for before/after to be the same.
                for (LValue intersect : intersection) {
                    if (!afterSSA.isValidReplacement(intersect, beforeSSA)) {
                        return;
                    }
                }
            }

            if (!afterSSA.isValidReplacement(lValue, beforeSSA)) return;
            LValueAssignmentExpressionRewriter rewriter = new LValueAssignmentExpressionRewriter(lValue, assignmentExpression, source);
            Expression replacement = rewriter.rewriteExpression(conditionalExpression, ifStatement.getSSAIdentifiers(), ifStatement, ExpressionRewriterFlags.LVALUE);
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


    private static boolean movableJump(JumpType jumpType) {
        switch (jumpType) {
            case BREAK:
            case GOTO_OUT_OF_IF:
            case CONTINUE:
                return false;
            default:
                return true;
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
                    a.targets.get(0) == b.targets.get(0) &&
//                    a.getJumpType() != JumpType.BREAK
                    a.getBlockIdentifiers().equals(b.getBlockIdentifiers())
                    ) {
                Op03SimpleStatement realTgt = a.targets.get(0);
                realTgt.removeSource(a);
                a.replaceTarget(realTgt, b);
                b.addSource(a);
                a.nopOut();
            }
        }


        // Do this pass first, as it needs spatial locality.
        for (int x = 0; x < size-1; ++x) {
            Op03SimpleStatement maybeJump = statements.get(x);
            if (maybeJump.containedStatement.getClass() == GotoStatement.class &&
                    maybeJump.getJumpType() != JumpType.BREAK &&
                    maybeJump.targets.size() == 1 &&
                    maybeJump.targets.get(0) == statements.get(x + 1)) {
                // But only if they're in the same blockset!
                if (maybeJump.getBlockIdentifiers().equals(statements.get(x+1).getBlockIdentifiers())) {
                    maybeJump.nopOut();
                } else {
                    // It might still be legit - if we've ended a loop, it's not.
                    Set<BlockIdentifier> changes = SetUtil.difference(maybeJump.getBlockIdentifiers(),statements.get(x+1).getBlockIdentifiers());
                    boolean ok = true;
                    for (BlockIdentifier change : changes) {
                        if (change.getBlockType().isLoop()) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        maybeJump.nopOut();
                    }
                }
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
                    if (jumpingInnerPriorTarget == innerStatement &&
                        movableJump(jumpInnerPrior.getJumpType())) {
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
                if (innerGoto.getJumpType() == JumpType.BREAK) continue;
                Op03SimpleStatement target = statement.targets.get(0);
                Op03SimpleStatement ultimateTarget = Misc.followNopGotoChain(target, false, false);
                if (target != ultimateTarget) {
                    ultimateTarget = maybeMoveTarget(ultimateTarget, statement, statements);
                    target.removeSource(statement);
                    statement.replaceTarget(target, ultimateTarget);
                    ultimateTarget.addSource(statement);
                }
            } else if (innerStatement.getClass() == IfStatement.class) {
                IfStatement ifStatement = (IfStatement) innerStatement;
                if (!movableJump(ifStatement.getJumpType())) continue;
                Op03SimpleStatement target = statement.targets.get(1);
                Op03SimpleStatement ultimateTarget = Misc.followNopGotoChain(target, false, false);
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

    public static void extractAssertionJumps(List<Op03SimpleStatement> in) {
        /*
         * If we have
         *
         * if () [non-goto-jump XX]
         * throw new AssertionError
         *
         * transform BACK to
         *
         * if () goto YYY
         * throw new AssertionError
         * YYY:
         * non-goto-jump XX
         */
        WildcardMatch wcm = new WildcardMatch();
        Statement assertionError = new ThrowStatement(wcm.getConstructorSimpleWildcard("exception", TypeConstants.ASSERTION_ERROR));

        for (int x=0,len=in.size();x<len;++x) {
            Op03SimpleStatement ostm = in.get(x);
            Statement stm = ostm.getStatement();
            if (stm.getClass() != IfStatement.class) continue;
            IfStatement ifStatement = (IfStatement)stm;
            if (ifStatement.getJumpType() == JumpType.GOTO) continue;
            Op03SimpleStatement next = in.get(x+1);
            if (next.getSources().size() != 1) continue;
            wcm.reset();
            if (!assertionError.equals(next.getStatement())) continue;
            if (!ostm.getBlockIdentifiers().equals(next.getBlockIdentifiers())) continue;
            GotoStatement reJumpStm = new GotoStatement();
            reJumpStm.setJumpType(ifStatement.getJumpType());
            Op03SimpleStatement reJump = new Op03SimpleStatement(ostm.getBlockIdentifiers(), reJumpStm, next.getIndex().justAfter());
            in.add(x+2, reJump);
            Op03SimpleStatement origTarget = ostm.getTargets().get(1);
            ostm.replaceTarget(origTarget, reJump);
            reJump.addSource(ostm);
            origTarget.replaceSource(ostm, reJump);
            reJump.addTarget(origTarget);
            ifStatement.setJumpType(JumpType.GOTO);
            len++;
        }
    }


    private static LinearScannedBlock getLinearScannedBlock(List<Op03SimpleStatement> statements, int idx, Op03SimpleStatement stm, BlockIdentifier blockIdentifier, boolean prefix) {
        Set<Op03SimpleStatement> found = SetFactory.newSet();
        int nextIdx = idx+(prefix?1:0);
        if (prefix) found.add(stm);
        int cnt = statements.size();
        do {
            Op03SimpleStatement nstm = statements.get(nextIdx);
            if (!nstm.getBlockIdentifiers().contains(blockIdentifier)) break;
            found.add(nstm);
            nextIdx++;
        } while (nextIdx < cnt);
        Set<Op03SimpleStatement> reachable = Misc.GraphVisitorBlockReachable.getBlockReachable(stm, blockIdentifier);
        if (!reachable.equals(found)) return null;
        nextIdx--;
        if (reachable.isEmpty()) return null;
        return new LinearScannedBlock(stm, statements.get(nextIdx), idx, nextIdx);
    }

    private static class SingleExceptionAddressing {
        BlockIdentifier tryBlockIdent;
        BlockIdentifier catchBlockIdent;
        LinearScannedBlock tryBlock;
        LinearScannedBlock catchBlock;

        private SingleExceptionAddressing(BlockIdentifier tryBlockIdent, BlockIdentifier catchBlockIdent, LinearScannedBlock tryBlock, LinearScannedBlock catchBlock) {
            this.tryBlockIdent = tryBlockIdent;
            this.catchBlockIdent = catchBlockIdent;
            this.tryBlock = tryBlock;
            this.catchBlock = catchBlock;
        }
    }

    private static SingleExceptionAddressing getSingleTryCatch(Op03SimpleStatement trystm, List<Op03SimpleStatement> statements) {
        int idx = statements.indexOf(trystm);
        TryStatement tryStatement = (TryStatement)trystm.getStatement();
        BlockIdentifier tryBlockIdent = tryStatement.getBlockIdentifier();
        LinearScannedBlock tryBlock = getLinearScannedBlock(statements, idx, trystm, tryBlockIdent, true);
        if (tryBlock == null) return null;
        Op03SimpleStatement catchs = trystm.getTargets().get(1);
        Statement testCatch = catchs.getStatement();
        if (!(testCatch instanceof CatchStatement)) return null;
        CatchStatement catchStatement = (CatchStatement)testCatch;
        BlockIdentifier catchBlockIdent = catchStatement.getCatchBlockIdent();
        LinearScannedBlock catchBlock = getLinearScannedBlock(statements, statements.indexOf(catchs), catchs, catchBlockIdent, true);
        if (catchBlock == null) return null;

        if (!catchBlock.isAfter(tryBlock)) return null;

        return new SingleExceptionAddressing(tryBlockIdent, catchBlockIdent, tryBlock, catchBlock);
    }

    private static boolean extractExceptionMiddle(Op03SimpleStatement trystm, List<Op03SimpleStatement> statements, SingleExceptionAddressing trycatch) {
        LinearScannedBlock tryBlock = trycatch.tryBlock;
        LinearScannedBlock catchBlock = trycatch.catchBlock;
        BlockIdentifier tryBlockIdent = trycatch.tryBlockIdent;
        BlockIdentifier catchBlockIdent = trycatch.catchBlockIdent;

        /*
         * Check that the catch block does not exit to the statement linearly after it.
         * (if there is such a statement).
         */
        int catchLast = catchBlock.getIdxLast();
        if (catchLast < statements.size()-1) {
            Op03SimpleStatement afterCatchBlock = statements.get(catchLast+1);
            for (Op03SimpleStatement source : afterCatchBlock.getSources()) {
                if (source.getBlockIdentifiers().contains(catchBlockIdent)) return false;
            }
        }

        if (catchBlock.immediatelyFollows(tryBlock)) return false;

        Set<BlockIdentifier> expected = trystm.getBlockIdentifiers();
        /*
         * Ok, we have a try block, a catch block and something inbetween them.  Verify that there are no jumps INTO
         * this intermediate code other than from the try or catch block, (or blocks in this range)
         * and that the blockset of the START of the try block is present the whole time.
         */
        Set<Op03SimpleStatement> middle = SetFactory.newSet();
        List<Op03SimpleStatement> toMove = ListFactory.newList();
        for (int x=tryBlock.getIdxLast()+1;x<catchBlock.getIdxFirst();++x) {
            Op03SimpleStatement stm = statements.get(x);
            middle.add(stm);
            toMove.add(stm);
        }
        for (int x=tryBlock.getIdxLast()+1;x<catchBlock.getIdxFirst();++x) {
            Op03SimpleStatement stm = statements.get(x);
            if (!stm.getBlockIdentifiers().containsAll(expected)) {
                return false;
            }
            for (Op03SimpleStatement source : stm.getSources()) {
                if (source.getIndex().isBackJumpTo(stm)) {
                    Set<BlockIdentifier> sourceBlocks = source.getBlockIdentifiers();
                    if (!(sourceBlocks.contains(tryBlockIdent) || (sourceBlocks.contains(catchBlockIdent)))) {
                        return false;
                    }
                }
            }
        }
        InstrIndex afterIdx = catchBlock.getLast().getIndex().justAfter();
        for (Op03SimpleStatement move : toMove) {
            move.setIndex(afterIdx);
            afterIdx = afterIdx.justAfter();
        }
        return true;
    }

    /*
     * If the try block jumps directly into the catch block, we might have an over-aggressive catch statement,
     * where the last bit should be outside it.
     *
     * As a conservative heuristic, treat this as valid if there are no other out of block forward jumps in the try
     * block. (strictly speaking this is pessimistic, and avoids indexed breaks and continues.  Revisit if examples
     * of those are found to be problematic).
     */
    private static void extractCatchEnd(Op03SimpleStatement trystm, List<Op03SimpleStatement> statements, SingleExceptionAddressing trycatch) {
        LinearScannedBlock tryBlock = trycatch.tryBlock;
        BlockIdentifier tryBlockIdent = trycatch.tryBlockIdent;
        BlockIdentifier catchBlockIdent = trycatch.catchBlockIdent;
        Op03SimpleStatement possibleAfterBlock = null;
        /*
         * If there IS a statement after the catch block, it can't be jumped to by the try block.
         * Otherwise, the catch block is the last code in the method.
         */
        if (trycatch.catchBlock.getIdxLast() < statements.size()-1) {
            Op03SimpleStatement afterCatch = statements.get(trycatch.catchBlock.getIdxLast()+1);
            for (Op03SimpleStatement source : afterCatch.getSources()) {
                if (source.getBlockIdentifiers().contains(tryBlockIdent)) return;
            }
        }
        for (int x = tryBlock.getIdxFirst()+1; x <= tryBlock.getIdxLast(); ++x) {
            List<Op03SimpleStatement> targets = statements.get(x).getTargets();
            for (Op03SimpleStatement target : targets) {
                if (target.getBlockIdentifiers().contains(catchBlockIdent)) {
                    if (possibleAfterBlock == null) {
                        possibleAfterBlock = target;
                    } else {
                        if (target != possibleAfterBlock) return;
                    }
                }
            }
        }
        if (possibleAfterBlock == null) return;

        /*
         * We require that possibleAfterBlock's block identifiers are the same as the try block, plus the catch ident.
         */
        Set<BlockIdentifier> tryStartBlocks = trycatch.tryBlock.getFirst().getBlockIdentifiers();
        Set<BlockIdentifier> possibleBlocks = possibleAfterBlock.getBlockIdentifiers();
        if (possibleBlocks.size() != tryStartBlocks.size()+1) return;

        if (!possibleBlocks.containsAll(tryStartBlocks)) return;
        if (!possibleBlocks.contains(catchBlockIdent)) return;

        /* We require that the reachable statements IN THE CATCH BLOCK are exactly the ones which are between
         * possibleAfterBlock and the end of the catch block.
         */
        int possibleIdx = statements.indexOf(possibleAfterBlock);
        LinearScannedBlock unmarkBlock = getLinearScannedBlock(statements, possibleIdx, possibleAfterBlock, catchBlockIdent, false);
        if (unmarkBlock == null) return;
        for (int x = unmarkBlock.getIdxFirst(); x<=unmarkBlock.getIdxLast(); ++x) {
            statements.get(x).getBlockIdentifiers().remove(catchBlockIdent);
        }
    }

    /*
     * If we've got any code between try and catch blocks, see if it can legitimately be moved
     * after the catch block.
     * (com/db4o/internal/Config4Class)
     *
     * For each try statement with one handler(*), find code between the end of the try and the start of the
     * handler.  If this is finally code, we should have picked that up by now.
     *
     * If that code starts/ends in the same blockset as the catch target/try source, and the catch block doesn't assume
     * it can fall through, then move the code after the catch block.
     *
     * This will be handled in a more general way by the op04 code, but doing it early gives us a better chance to spot
     * some issues.
     *
     */
    public static void extractExceptionMiddle(List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tryStatements = Functional.filter(in, new Op03SimpleStatement.ExactTypeFilter<TryStatement>(TryStatement.class));
        if (tryStatements.isEmpty()) return;
        Collections.reverse(tryStatements);
        for (Op03SimpleStatement tryStatement : tryStatements) {
            if (tryStatement.getTargets().size() != 2) continue;
            SingleExceptionAddressing trycatch = getSingleTryCatch(tryStatement, in);
            if (trycatch == null) continue;
            if (extractExceptionMiddle(tryStatement, in, trycatch)) {
                // We will only have ever moved something downwards, and won't have removed any tries, so this doesn't
                // invalidate any loop invariants.
                Cleaner.sortAndRenumberInPlace(in);
                trycatch.tryBlock.reindex(in);
                trycatch.catchBlock.reindex(in);
            }
            extractCatchEnd(tryStatement, in, trycatch);
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
     *
     * RequireDirectAfter - y MUST equal x+1.
     */
    public static void rewriteNegativeJumps(List<Op03SimpleStatement> statements, boolean requireChainedConditional) {
        List<Op03SimpleStatement> removeThese = ListFactory.newList();
        for (int x = 0; x < statements.size() - 2; ++x) {
            Op03SimpleStatement aStatement = statements.get(x);
            Statement innerAStatement = aStatement.getStatement();
            if (innerAStatement instanceof IfStatement) {
                Op03SimpleStatement zStatement = statements.get(x + 1);
                Op03SimpleStatement xStatement = statements.get(x + 2);

                if (requireChainedConditional) {
                    if (!(xStatement.getStatement() instanceof IfStatement)) continue;
                }

                if (aStatement.targets.get(0) == zStatement &&
                    aStatement.targets.get(1) == xStatement) {

                    Statement innerZStatement = zStatement.getStatement();
                    if (innerZStatement.getClass() == GotoStatement.class) {
                        // Yep, this is it.
                        Op03SimpleStatement yStatement = zStatement.targets.get(0);

                        if (yStatement == zStatement) continue;

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
        Set<LValue> res = SetFactory.newOrderedSet();
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

    public static Op03SimpleStatement findMovableAssignment(Op03SimpleStatement start, LValue lValue) {
        Op03SimpleStatement current = Misc.findSingleBackSource(start);
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
                    if (SSAIdentifierUtils.isMovableUnder(lValueUsageCollector.getUsedLValues(), lValue, start.ssaIdentifiers, current.ssaIdentifiers)) {
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

    private static List<Op03SimpleStatement> getMutations(List<Op03SimpleStatement> backSources, LValue loopVariable, BlockIdentifier whileBlockIdentifier) {

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
                return null;
            }
        }
        return mutations;
    }

    private static void rewriteWhileAsFor(Op03SimpleStatement statement, List<Op03SimpleStatement> statements, boolean aggcapture) {
        // Find the backwards jumps to this statement
        List<Op03SimpleStatement> backSources = Functional.filter(statement.sources, new Misc.IsBackJumpTo(statement.index));
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
        Set<LValue> reverseOrderedMutatedPossibilities = null;
        for (Op03SimpleStatement source : backSources) {
            Set<LValue> incrPoss = findForInvariants(source, whileBlockIdentifier);
            if (reverseOrderedMutatedPossibilities == null) {
                reverseOrderedMutatedPossibilities = incrPoss;
            } else {
                reverseOrderedMutatedPossibilities.retainAll(incrPoss);
            }
            // If there are no possibilites, then we can't do anything.
            if (reverseOrderedMutatedPossibilities.isEmpty()) {
                logger.info("No invariant possibilities on source\n");
                return;
            }
        }
        if (reverseOrderedMutatedPossibilities == null || reverseOrderedMutatedPossibilities.isEmpty()) {
            logger.info("No invariant intersection\n");
            return;
        }
        loopVariablePossibilities.retainAll(reverseOrderedMutatedPossibilities);
        // Intersection between incremented / tested.
        if (loopVariablePossibilities.isEmpty()) {
            logger.info("No invariant intersection\n");
            return;
        }

        Op03SimpleStatement loopVariableOp = null;
        LValue loopVariable = null;
        for (LValue loopVariablePoss : loopVariablePossibilities) {

            //
            // If possible, go back and find an unconditional assignment to the loop variable.
            // We have to be sure that moving this to the for doesn't violate SSA versions.
            //
            Op03SimpleStatement initialValue = findMovableAssignment(statement, loopVariablePoss);
            if (initialValue != null) {
                if (loopVariableOp == null || initialValue.getIndex().isBackJumpTo(loopVariableOp)) {
                    loopVariableOp = initialValue;
                    loopVariable = loopVariablePoss;
                }
            }
        }
        if (loopVariable == null) return;
        AssignmentSimple initalAssignmentSimple = null;


        List<AbstractAssignmentExpression> postUpdates = ListFactory.newList();
        List<List<Op03SimpleStatement>> usedMutatedPossibilities = ListFactory.newList();
        boolean usesLoopVar = false;
        for (LValue otherMutant : reverseOrderedMutatedPossibilities) {
            List<Op03SimpleStatement> othermutations = getMutations(backSources, otherMutant, whileBlockIdentifier);
            if (othermutations == null) continue;

            // We abort if we're about to lift a mutation which isn't in the predicate.
            // This is not necessarily the best idea, but otherwise we might lift all sorts of stuff,
            // leading to very ugly code.
            if (!loopVariablePossibilities.contains(otherMutant)) {
                if (!aggcapture) break;
            }
            if (otherMutant.equals(loopVariable)) usesLoopVar = true;

            AbstractAssignmentExpression postUpdate2 = ((AbstractAssignment)(othermutations.get(0).getStatement())).getInliningExpression();
            postUpdates.add(postUpdate2);
            usedMutatedPossibilities.add(othermutations);
        }
        if (!usesLoopVar) return;

        Collections.reverse(postUpdates);
        for (List<Op03SimpleStatement> lst : usedMutatedPossibilities) {
            for (Op03SimpleStatement op : lst) {
                op.nopOut();
            }
        }

        if (loopVariableOp != null) {
            initalAssignmentSimple = (AssignmentSimple) loopVariableOp.containedStatement;
            loopVariableOp.nopOut();
        }

        whileBlockIdentifier.setBlockType(BlockType.FORLOOP);

        whileStatement.replaceWithForLoop(initalAssignmentSimple, postUpdates);

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

    public static void rewriteWhilesAsFors(Options options, List<Op03SimpleStatement> statements) {
        // Find all the while loops beginnings.
        List<Op03SimpleStatement> whileStarts = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return (in.containedStatement instanceof WhileStatement) && ((WhileStatement) in.containedStatement).getBlockIdentifier().getBlockType() == BlockType.WHILELOOP;
            }
        });
        boolean aggcapture = options.getOption(OptionsImpl.FOR_LOOP_CAPTURE) == Troolean.TRUE;
        for (Op03SimpleStatement whileStart : whileStarts) {
            rewriteWhileAsFor(whileStart, statements, aggcapture);
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

            int endIdx = statements.indexOf(end);
            if (endIdx < statements.size() - 2) {
                Op03SimpleStatement shuffled = statements.get(endIdx + 1);
                for (Op03SimpleStatement shuffledSource : shuffled.sources) {
                    if (shuffledSource.getStatement() instanceof JumpingStatement) {
                        JumpingStatement jumpingStatement = (JumpingStatement) shuffledSource.getStatement();
                        if (jumpingStatement.getJumpType() == JumpType.BREAK) {
                            jumpingStatement.setJumpType(JumpType.GOTO);
                        }
                    }
                }
            }
            statements.add(endIdx + 1, after);
            // Any break statements which were targetting end+1 are now invalid.....
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
        Cleaner.reindexInPlace(statements);
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
                    Set<BlockIdentifier> blocksEnded = targetStatement.getBlocksEnded();
                    if (!blocksEnded.isEmpty()) {
                        BlockIdentifier outermostContainedIn = BlockIdentifier.getOutermostContainedIn(blocksEnded, statement.containedInBlocks);
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

    public static boolean classifyAnonymousBlockGotos(List<Op03SimpleStatement> in, boolean agressive) {
        boolean result = false;
        int agressiveOffset = agressive ? 1 : 0;

        /*
         * Now, finally, for each unclassified goto, see if we can mark it as a break out of an anonymous block.
         */
        for (Op03SimpleStatement statement : in) {
            Statement inner = statement.getStatement();
            if (inner instanceof JumpingStatement) {
                JumpingStatement jumpingStatement = (JumpingStatement) inner;
                JumpType jumpType = jumpingStatement.getJumpType();
                if (jumpType != JumpType.GOTO) continue;
                Op03SimpleStatement targetStatement = (Op03SimpleStatement) jumpingStatement.getJumpTarget().getContainer();
                boolean isForwardJump = targetStatement.getIndex().isBackJumpTo(statement);
                if (isForwardJump) {
                    Set<BlockIdentifier> targetBlocks = targetStatement.getBlockIdentifiers();
                    Set<BlockIdentifier> srcBlocks = statement.getBlockIdentifiers();
                    if (targetBlocks.size() < srcBlocks.size() + agressiveOffset  && srcBlocks.containsAll(targetBlocks)) {
                        /*
                         * Remove all the switch blocks from srcBlocks.
                         */
                        srcBlocks = Functional.filterSet(srcBlocks, new Predicate<BlockIdentifier>() {
                            @Override
                            public boolean test(BlockIdentifier in) {
                                BlockType blockType = in.getBlockType();
                                if (blockType == BlockType.CASE) return false;
                                if (blockType == BlockType.SWITCH) return false;
                                return true;
                            }
                        });
                        if (targetBlocks.size() < srcBlocks.size() + agressiveOffset && srcBlocks.containsAll(targetBlocks)) {
                            /*
                             * Break out of an anonymous block
                             */
                            /*
                             * Should we now be re-looking at ALL other forward jumps to this target?
                             */
                            jumpingStatement.setJumpType(JumpType.BREAK_ANONYMOUS);
                            result = true;
                        }
                    }
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
            statements = Cleaner.sortAndRenumber(statements);
            // This is being done twice deliberately.  Should rewrite rewriteNegativeJumps to iterate.
            // in practice 2ce is fine.
            // see /com/db4o/internal/btree/BTreeNode.class
            Op03SimpleStatement.rewriteNegativeJumps(statements, false);
            Op03SimpleStatement.rewriteNegativeJumps(statements, false);
        }
        return statements;
    }

    private static boolean moveable(Statement statement) {
        Class<?> clazz = statement.getClass();
        if (clazz == Nop.class) return true;
        if (clazz == AssignmentSimple.class) return true;
        if (clazz == CommentStatement.class) return true;
        if (clazz == ExpressionStatement.class) return true;
        if (clazz == IfExitingStatement.class) return true;
        return false;
    }

    private static boolean pushThroughGoto(Method method, Op03SimpleStatement forwardGoto, List<Op03SimpleStatement> statements) {

        if (forwardGoto.sources.size() != 1) return false;

        final Op03SimpleStatement tgt = forwardGoto.getTargets().get(0);
        int idx = statements.indexOf(tgt);
        if (idx == 0) return false;
        final Op03SimpleStatement before = statements.get(idx - 1);
        // TODO : should be simple to verify that the first test is uneccessary.
        if (tgt.getSources().contains(before)) return false;
        if (tgt.getSources().size() != 1) return false;

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
        Set<BlockIdentifier> beforeLoopBlocks = SetFactory.newSet(Functional.filterSet(before.getBlockIdentifiers(), isLoopBlock));
        Set<BlockIdentifier> tgtLoopBlocks = SetFactory.newSet(Functional.filterSet(tgt.getBlockIdentifiers(), isLoopBlock));
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

        Set<BlockIdentifier> exceptionBlocks = SetFactory.newSet(Functional.filterSet(tgt.getBlockIdentifiers(), exceptionFilter));
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
            // If sources > 1, we can't continue processing after this one, but we can do this one.
            boolean abortNext = (tryMoveThis.sources.size() != 1);
            // Is it in the same exception blocks?
            Set<BlockIdentifier> moveEB = SetFactory.newSet(Functional.filterSet(forwardGoto.getBlockIdentifiers(), exceptionFilter));
            if (!moveEB.equals(exceptionBlocks)) return success;
            /* Move this instruction through the goto
             */
            forwardGoto.sources.clear();
            for (Op03SimpleStatement beforeTryMove : tryMoveThis.sources) {
                beforeTryMove.replaceTarget(tryMoveThis, forwardGoto);
                forwardGoto.sources.add(beforeTryMove);
            }
            tryMoveThis.sources.clear();
            tryMoveThis.sources.add(forwardGoto);
            forwardGoto.replaceTarget(lastTarget, tryMoveThis);
            tryMoveThis.replaceTarget(forwardGoto, lastTarget);
            lastTarget.replaceSource(forwardGoto, tryMoveThis);

            tryMoveThis.index = beforeTgt;
            beforeTgt = beforeTgt.justBefore();

            tryMoveThis.containedInBlocks.clear();
            tryMoveThis.containedInBlocks.addAll(lastTarget.containedInBlocks);
            lastTarget = tryMoveThis;
            nextCandidateIdx--;
            success = true;
            if (abortNext) return success;
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


    public JumpType getJumpType() {
        if (containedStatement instanceof JumpingStatement) {
            return ((JumpingStatement) containedStatement).getJumpType();
        }
        return JumpType.NONE;
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


    private static void combineTryCatchBlocks(final Op03SimpleStatement tryStatement, List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory) {
        Set<Op03SimpleStatement> allStatements = SetFactory.newSet();
        TryStatement innerTryStatement = (TryStatement) tryStatement.getStatement();

        allStatements.addAll(Misc.GraphVisitorBlockReachable.getBlockReachable(tryStatement, innerTryStatement.getBlockIdentifier()));

        // all in block, reachable
        for (Op03SimpleStatement target : tryStatement.getTargets()) {
            if (target.containedStatement instanceof CatchStatement) {
                CatchStatement catchStatement = (CatchStatement) target.containedStatement;
                allStatements.addAll(Misc.GraphVisitorBlockReachable.getBlockReachable(target, catchStatement.getCatchBlockIdent()));
            }
        }

        /* Add something inFRONT of the try statement which is NOT going to be in this block, which can adopt it
         * (This is obviously an unreal artifact)
         */
        /* See Tock test for why we should only extend try/catch.
         */
        Set<BlockIdentifier> tryBlocks = tryStatement.containedInBlocks;
        tryBlocks = SetFactory.newSet(Functional.filter(tryBlocks, new Predicate<BlockIdentifier>() {
            @Override
            public boolean test(BlockIdentifier in) {
                return in.getBlockType() == BlockType.TRYBLOCK || in.getBlockType() == BlockType.CATCHBLOCK;
            }
        }));
        if (tryBlocks.isEmpty()) return;

        List<Op03SimpleStatement> orderedStatements = ListFactory.newList(allStatements);
        Collections.sort(orderedStatements, new CompareByIndex(false));

        for (Op03SimpleStatement statement : orderedStatements) {
            for (BlockIdentifier ident : tryBlocks) {
                if (!statement.containedInBlocks.contains(ident) &&
                     statement.sources.contains(statement.linearlyPrevious) &&
                     statement.linearlyPrevious.containedInBlocks.contains(ident)) {
                    statement.addPossibleExitFor(ident);
                }
            }
            statement.containedInBlocks.addAll(tryBlocks);
        }
    }

    private Set<BlockIdentifier> possibleExitsFor = null;
    private void addPossibleExitFor(BlockIdentifier ident) {
        if (possibleExitsFor == null) possibleExitsFor = SetFactory.newOrderedSet();
        possibleExitsFor.add(ident);
    }
    public boolean isPossibleExitFor(BlockIdentifier ident) {
        return possibleExitsFor != null && possibleExitsFor.contains(ident);
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

        // Handle duplicates - is there a neater way? (Avoiding filter pass).
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        for (Op03SimpleStatement last : lastStatements) {
            if (!seen.add(last)) continue;
            GotoStatement gotoStatement = (GotoStatement) last.containedStatement;
            gotoStatement.setJumpType(JumpType.END_BLOCK);
            last.replaceTarget(target, proxy);
            target.removeSource(last);
            proxy.addSource(last);
        }
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
            if (statement.isAgreedNop()) {
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
     * We make use of known ordering here - we expect contents of a catch block to be directly after it, and eligible
     * instructions to be after that.
     */
    private static void extendCatchBlock(Op03SimpleStatement catchStart, List<Op03SimpleStatement> in) {
        int idx = in.indexOf(catchStart);
        CatchStatement catchStatement = (CatchStatement)catchStart.getStatement();
        BlockIdentifier blockIdentifier = catchStatement.getCatchBlockIdent();
        if (catchStart.getTargets().size() != 1) return;
        idx++;
        Op03SimpleStatement next = in.get(idx);
        if (next != catchStart.getTargets().get(0)) return;
        int tot = in.size();
        while (idx < tot && in.get(idx).getBlockIdentifiers().contains(blockIdentifier)) {
            idx++;
        }
        if (idx >= tot) return;
        /*
         * We assume we have a linear relationship - this is obviously quite poor, but serves to capture
         * the nastier cases dex2jar generates.
         */
        Op03SimpleStatement prev = in.get(idx-1);
        Set<BlockIdentifier> identifiers = prev.getBlockIdentifiers();
        while (idx < tot) {
            Op03SimpleStatement stm = in.get(idx);
            if (stm.getBlockIdentifiers().size() != identifiers.size() - 1) return;
            List<BlockIdentifier> diff =  SetUtil.differenceAtakeBtoList(identifiers, stm.getBlockIdentifiers());
            if (diff.size() != 1) return;
            if (diff.get(0) != blockIdentifier) return;
            /*
             * Verify that all stm's parents are in the catch block, and that diff has only <=one target.
             */
            if (stm.getTargets().size() > 1) return;
            for (Op03SimpleStatement source : stm.getSources()){
                if (!source.getBlockIdentifiers().contains(blockIdentifier)) return;
            }
            if (!stm.getSources().contains(prev)) return;
            // Ok, add.
            stm.getBlockIdentifiers().add(blockIdentifier);
            prev = stm;
        }
    }

    /*
     * After some rewriting operations, we are left with code (return statements mainly!) which couldn't earlier
     * be pulled inside a block, because it had multiple sources - but now can.
     */
    public static void extendCatchBlocks(List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> catchStarts = Functional.filter(in, new TypeFilter<CatchStatement>(CatchStatement.class));
        for (Op03SimpleStatement catchStart : catchStarts) {
            CatchStatement catchStatement = (CatchStatement) catchStart.containedStatement;
            if (catchStatement.getCatchBlockIdent() != null) {
                extendCatchBlock(catchStart, in);
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

        Op03SimpleStatement lastStatement = null;
        Op03SimpleStatement currentStatement = tryStatement.targets.get(0);
        int x = in.indexOf(currentStatement);

        List<Op03SimpleStatement> jumps = ListFactory.newList();

        while (currentStatement.getBlockIdentifiers().contains(tryBlockIdent)) {
            ++x;
            if (x >= in.size()) {
                return;
            }
            lastStatement = currentStatement;
            if (currentStatement.getStatement() instanceof JumpingStatement) {
                jumps.add(currentStatement);
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

        ExceptionCheck exceptionCheck = new ExceptionCheckImpl(dcCommonState, caught);

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
            lastStatement = currentStatement;
            if (currentStatement.getStatement() instanceof JumpingStatement) {
                jumps.add(currentStatement);
            }
            currentStatement = nextStatement;
        }
        if (lastStatement != null &&  lastStatement.getTargets().isEmpty()) {
            // We have opportunity to rescan and see if there is a UNIQUE forward jump out.
            Set<Op03SimpleStatement> outTargets = SetFactory.newSet();
            for (Op03SimpleStatement jump : jumps) {
                JumpingStatement jumpingStatement = (JumpingStatement)jump.getStatement();
                // This is ugly.  I'm sure I do it elsewhere.   Refactor.
                int idx = jumpingStatement.isConditional() ? 1 : 0;
                if (idx >= jump.getTargets().size()) return; // can't happen.
                Op03SimpleStatement jumpTarget = jump.getTargets().get(idx);
                if (jumpTarget.getIndex().isBackJumpFrom(jump)) continue;
                if (jumpTarget.getBlockIdentifiers().contains(tryBlockIdent)) continue;
                outTargets.add(jumpTarget);
            }
            if (outTargets.size() == 1) {
                Op03SimpleStatement replace = outTargets.iterator().next();
                Op03SimpleStatement newJump = new Op03SimpleStatement(lastStatement.getBlockIdentifiers(), new GotoStatement(), lastStatement.getIndex().justAfter());
                newJump.addTarget(replace);
                replace.addSource(newJump);
                for (Op03SimpleStatement jump : jumps) {
                    if (jump.getTargets().contains(replace)) {
                        jump.replaceTarget(replace, newJump);
                        newJump.addSource(jump);
                        replace.removeSource(jump);
                    }
                }
                in.add(in.indexOf(lastStatement)+1, newJump);
            }
        }
    }

    public static void extendTryBlocks(DCCommonState dcCommonState, List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> tries = Functional.filter(in, new TypeFilter<TryStatement>(TryStatement.class));
        for (Op03SimpleStatement tryStatement : tries) {
            extendTryBlock(tryStatement, in, dcCommonState);
        }
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
            statements = Cleaner.removeUnreachableCode(statements, false);
            statements = Cleaner.sortAndRenumber(statements);
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

    /*
     * and this is an even more aggressive version - simply walk the code in order (yes, it needs to be ordered
     * at this point), and fill in any missing blocks.
     */
    public static void rejoinBlocks2(List<Op03SimpleStatement> statements) {
        Map<BlockIdentifier, Integer> lastSeen = MapFactory.newMap();
        for (int x=0, len=statements.size();x<len;++x) {
            Op03SimpleStatement stm = statements.get(x);
            for (BlockIdentifier identifier : stm.getBlockIdentifiers()) {

                Integer prev = lastSeen.get(identifier)  ;
                if (prev != null && prev < x-1) {
                    for (int y=prev+1;y<x;++y) {
                        statements.get(y).getBlockIdentifiers().add(identifier);
                    }
                }
                lastSeen.put(identifier, x);
            }
        }
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

    public static List<Op03SimpleStatement> convertIndirectTryLeavesToAnonymousBreaks(List<Op03SimpleStatement> statements) {
        Set<BlockIdentifier> blocksToRemoveCompletely = SetFactory.newSet();

        for (Op03SimpleStatement in : statements) {
            Statement statement = in.getStatement();
            if (!(statement instanceof IfStatement)) continue;
            IfStatement ifStatement = (IfStatement) statement;
            if (ifStatement.hasElseBlock()) continue;
            Op03SimpleStatement afterIf = in.targets.get(1);
            Statement indirect = afterIf.getStatement();
            if (indirect.getClass() != GotoStatement.class) continue;
            GotoStatement gotoStatement = (GotoStatement) indirect;
            if (gotoStatement.getJumpType() != JumpType.GOTO_OUT_OF_TRY) continue;
            Op03SimpleStatement eventualTarget = afterIf.targets.get(0);
            ifStatement.setJumpType(JumpType.BREAK_ANONYMOUS);
            in.replaceTarget(afterIf, eventualTarget);
            afterIf.removeSource(in);
            eventualTarget.addSource(in);
            blocksToRemoveCompletely.add(ifStatement.getKnownIfBlock());
            ifStatement.setKnownBlocks(null, null);
        }

        if (blocksToRemoveCompletely.isEmpty()) return statements;

        for (Op03SimpleStatement stm : statements) {
            stm.getBlockIdentifiers().removeAll(blocksToRemoveCompletely);
        }
        statements = Cleaner.removeUnreachableCode(statements, false);
        return statements;
    }

    public static void labelAnonymousBlocks(List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory) {
        List<Op03SimpleStatement> anonBreaks = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                Statement statement = in.getStatement();
                if (!(statement instanceof JumpingStatement)) return false;
                JumpType jumpType = ((JumpingStatement) statement).getJumpType();
                return jumpType == JumpType.BREAK_ANONYMOUS;
            }
        });
        if (anonBreaks.isEmpty()) return;

        /*
         * Collect the unique set of targets for the anonymous breaks.
         */
        Set<Op03SimpleStatement> targets = SetFactory.newOrderedSet();
        for (Op03SimpleStatement anonBreak : anonBreaks) {
            JumpingStatement jumpingStatement = (JumpingStatement) anonBreak.getStatement();
            Op03SimpleStatement anonBreakTarget = (Op03SimpleStatement) jumpingStatement.getJumpTarget().getContainer();
            if (anonBreakTarget.getStatement() instanceof AnonBreakTarget) continue;
            targets.add(anonBreakTarget);
        }

        int idx = 0;
        for (Op03SimpleStatement target : targets) {
            BlockIdentifier blockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.ANONYMOUS);
            InstrIndex targetIndex = target.getIndex();
            Op03SimpleStatement anonTarget = new Op03SimpleStatement(
                    target.getBlockIdentifiers(), new AnonBreakTarget(blockIdentifier), targetIndex.justBefore());
            List<Op03SimpleStatement> sources = ListFactory.newList(target.getSources());
            for (Op03SimpleStatement source : sources) {
                if (targetIndex.isBackJumpTo(source)) {
                    target.removeSource(source);
                    source.replaceTarget(target, anonTarget);
                    anonTarget.addSource(source);
                }
            }
            target.addSource(anonTarget);
            anonTarget.addTarget(target);
            int pos = statements.indexOf(target);
            statements.add(pos, anonTarget);
        }
    }

    public static void replaceStackVarsWithLocals(List<Op03SimpleStatement> statements) {
        StackVarToLocalRewriter rewriter = new StackVarToLocalRewriter();
        for (Op03SimpleStatement statement : statements) {
            statement.rewrite(rewriter);
        }
    }

    public static void narrowAssignmentTypes(Method method, List<Op03SimpleStatement> statements) {
        NarrowingTypeRewriter narrowingTypeRewriter = new NarrowingTypeRewriter();
        narrowingTypeRewriter.rewrite(method, statements);
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
