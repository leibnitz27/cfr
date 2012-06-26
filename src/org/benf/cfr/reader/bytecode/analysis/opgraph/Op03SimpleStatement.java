package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 19/03/2012
 * Time: 06:52
 * To change this template use File | Settings | File Templates.
 */
public class Op03SimpleStatement implements MutableGraph<Op03SimpleStatement>, Dumpable, StatementContainer, IndexedStatement {
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
    private final List<BlockIdentifier> containedInBlocks = ListFactory.newList();
    //
    // blocks ended just before this.  (used to resolve break statements).
    //
    private final List<BlockIdentifier> immediatelyAfterBlocks = ListFactory.newList();

    public Op03SimpleStatement(Op02WithProcessedDataAndRefs original, Statement statement) {
        this.containedStatement = statement;
        this.isNop = false;
        this.index = original.getIndex();
        this.ssaIdentifiers = new SSAIdentifiers();
        this.containedInBlocks.addAll(original.getContainedInTheseBlocks());
        statement.setContainer(this);
    }

    private Op03SimpleStatement(List<BlockIdentifier> containedIn, Statement statement, InstrIndex index) {
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

    @Override
    public SSAIdentifiers getSSAIdentifiers() {
        return ssaIdentifiers;
    }

    private boolean isNop() {
        return isNop;
    }

    private void replaceTarget(Op03SimpleStatement oldTarget, Op03SimpleStatement newTarget) {
//        System.out.println("Replacing target + " + oldTarget + " with " + newTarget + " from " + this);
        int index = targets.indexOf(oldTarget);
        if (index == -1) {
            throw new ConfusedCFRException("Invalid target");
        }
        targets.set(index, newTarget);
    }

    private void replaceSingleSourceWith(Op03SimpleStatement oldSource, List<Op03SimpleStatement> newSources) {
//        System.out.println("Replacing source + " + oldSource + " with " + newSources + " from " + this);
        if (!sources.remove(oldSource)) throw new ConfusedCFRException("Invalid source");
        sources.addAll(newSources);
    }

    private void replaceSource(Op03SimpleStatement oldSource, Op03SimpleStatement newSource) {
//        System.out.println("Replacing source + " + oldSource + " with " + newSource + " from " + this);
        int index = sources.indexOf(oldSource);
        if (index == -1) {
            throw new ConfusedCFRException("Invalid source");
        }
        sources.set(index, newSource);
    }

    private void removeSource(Op03SimpleStatement oldSource) {
        //       System.out.println("Removing source + " + oldSource + " from " + this);
        if (!sources.remove(oldSource)) {
            throw new ConfusedCFRException("Invalid source, tried to remove " + oldSource + "\nfrom " + this + "\nbut was not a source.");
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

    private void markPreBlockStatement(BlockIdentifier blockIdentifier, Op03SimpleStatement blockEnd, List<Op03SimpleStatement> statements) {
        if (thisComparisonBlock != null) {
            throw new ConfusedCFRException("Statement marked as the start of multiple blocks");
        }
        this.thisComparisonBlock = blockIdentifier;
        switch (blockIdentifier.getBlockType()) {
            case WHILELOOP: {
                IfStatement ifStatement = (IfStatement) containedStatement;
                ifStatement.replaceWithWhileLoopStart(blockIdentifier);
                Op03SimpleStatement whileEndTarget = targets.get(1);
                if (index.isBackJumpTo(whileEndTarget)) {
                    // If the while statement's 'not taken' is a back jump, we normalise
                    // to a forward jump to after the block, and THAT gets to be the back jump.
                    // Note that this can't be done before "Remove pointless jumps".
                    // The blocks that this new statement is in are the same as my blocks, barring
                    // blockIdentifier.
                    List<BlockIdentifier> backJumpContainedIn = ListFactory.newList(containedInBlocks);
                    backJumpContainedIn.remove(blockIdentifier);
                    Op03SimpleStatement backJump = new Op03SimpleStatement(backJumpContainedIn, new GotoStatement(), blockEnd.index.justBefore());
                    whileEndTarget.replaceSource(this, backJump);
                    replaceTarget(whileEndTarget, backJump);
                    backJump.addSource(this);
                    backJump.addTarget(whileEndTarget);
                    // We have to manipulate the statement list immediately, as we're relying on spatial locality elsewhere.
                    statements.add(statements.indexOf(blockEnd), backJump);
                }
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

    private void collect(LValueAssignmentCollector lValueAssigmentCollector) {
        containedStatement.getLValueEquivalences(lValueAssigmentCollector);
    }

    private void condense(LValueRewriter lValueRewriter) {
        containedStatement.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers);
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

    public static class CompareByIndex implements Comparator<Op03SimpleStatement> {
        @Override
        public int compare(Op03SimpleStatement a, Op03SimpleStatement b) {
            return a.getIndex().compareTo(b.getIndex());
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

    private void dumpInner(Dumper dumper) {
        if (needsLabel()) dumper.print(getLabel() + ":\n");
        for (BlockIdentifier blockIdentifier : containedInBlocks) {
            dumper.print(blockIdentifier + " ");
        }
        int indent = dumper.getIndent();
        getStatement().dump(dumper);
    }

    @Override
    public void dump(Dumper dumper) {
        List<Op03SimpleStatement> reachableNodes = ListFactory.newList();
        GraphVisitorCallee graphVisitorCallee = new GraphVisitorCallee(reachableNodes);
        GraphVisitor<Op03SimpleStatement> visitor = new GraphVisitorDFS<Op03SimpleStatement>(this, graphVisitorCallee);
        visitor.process();

        Collections.sort(reachableNodes, new CompareByIndex());
        for (Op03SimpleStatement op : reachableNodes) {
            op.dumpInner(dumper);
        }
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
            /* If anything's changed, we need to check this statements children. */
            if (changed) {
                toProcess.addAll(statement.getTargets());
            }
        }
    }

    public static void condenseLValues(List<Op03SimpleStatement> statements) {
        LValueAssignmentCollector lValueAssigmentCollector = new LValueAssignmentCollector();
        for (Op03SimpleStatement statement : statements) {
            statement.collect(lValueAssigmentCollector);
        }

        LValueAssignmentCollector.FirstPassRewriter multiRewriter = lValueAssigmentCollector.getFirstPassRewriter();
        for (Op03SimpleStatement statement : statements) {
            statement.condense(multiRewriter);
        }
        multiRewriter.inferAliases();

        for (Op03SimpleStatement statement : statements) {
            statement.condense(lValueAssigmentCollector);
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
     * We assume that statements are ordered.
     */
    public static void rewriteNegativeJumps(List<Op03SimpleStatement> statements) {
        for (int x = 0; x < statements.size() - 2; ++x) {
            Op03SimpleStatement aStatement = statements.get(x);
            Statement innerAStatement = aStatement.getStatement();
            if (innerAStatement instanceof IfStatement) {
                if (aStatement.targets.get(0) == statements.get(x + 1) &&
                        aStatement.targets.get(1) == statements.get(x + 2)) {
                    Op03SimpleStatement zStatement = statements.get(x + 1);
                    Statement innerZStatement = zStatement.getStatement();
                    if (innerZStatement instanceof GotoStatement) {
                        // Yep, this is it.
                        Op03SimpleStatement yStatement = zStatement.targets.get(0);
                        Op03SimpleStatement xStatement = statements.get(x + 2);

                        // Order is important.
                        aStatement.targets.set(1, yStatement);

                        yStatement.replaceSource(zStatement, aStatement);
                        xStatement.replaceSource(aStatement, zStatement);
                        zStatement.replaceTarget(yStatement, xStatement);
                        zStatement.containedStatement = new Nop();
                    }
                }
            }
        }
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
            if (current.containedStatement instanceof Assignment) {
                Assignment assignment = (Assignment) current.containedStatement;
                LValue assigned = assignment.getCreatedLValue();
                if (invariant.equals(assigned)) {
                    Expression rValue = assignment.getRValue();
                    if (rValue instanceof ArithmeticOperation) {
                        ArithmeticOperation arithmeticOperation = (ArithmeticOperation) rValue;
                        if (arithmeticOperation.isLiteralFunctionOf(assigned)) return current;
                    }
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
            if (current.containedStatement instanceof Assignment) {
                Assignment assignment = (Assignment) current.containedStatement;
                LValue assigned = assignment.getCreatedLValue();
                Expression rValue = assignment.getRValue();
                if (rValue instanceof ArithmeticOperation) {
                    ArithmeticOperation arithmeticOperation = (ArithmeticOperation) rValue;
                    if (arithmeticOperation.isLiteralFunctionOf(assigned)) res.add(assigned);
                }
            }
            if (current.sources.size() > 1) break;
            Op03SimpleStatement next = current.sources.get(0);
            if (!current.index.isBackJumpTo(next)) break;
            current = next;
        }
        return res;
    }

    private static Op03SimpleStatement findMovableAssignment(Op03SimpleStatement start, LValue lValue) {
        List<Op03SimpleStatement> startSources = Functional.filter(start.sources, new IsForwardJumpTo(start.index));
        if (startSources.size() != 1) {
            System.out.println("** Too many back sources");
            return null;
        }
        Op03SimpleStatement current = startSources.iterator().next();
        do {
            if (current.containedStatement instanceof Assignment) {
                Assignment assignment = (Assignment) current.containedStatement;
                if (assignment.getCreatedLValue().equals(lValue)) {
                    /* Verify that everything on the RHS is at the correct version */
                    Expression rhs = assignment.getRValue();
                    LValueUsageCollector lValueUsageCollector = new LValueUsageCollector();
                    rhs.collectUsedLValues(lValueUsageCollector);
                    if (SSAIdentifierUtils.isMovableUnder(lValueUsageCollector.getUsedLValues(), start.ssaIdentifiers, current.ssaIdentifiers)) {
                        return current;
                    } else {
                        System.out.println("** incompatible sources");
                        return null;
                    }
                }
            }
            if (current.sources.size() != 1) {
                System.out.println("** too many sources");
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
            System.out.println("No loop variable possibilities\n");
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
                System.out.println("No invariant possibilities on source\n");
                return;
            }
        }
        loopVariablePossibilities.retainAll(mutatedPossibilities);
        // Intersection between incremented / tested.
        if (loopVariablePossibilities.isEmpty()) {
            System.out.println("No invariant intersection\n");
            return;
        }

        // If we've got choices, ignore currently.
        if (loopVariablePossibilities.size() > 1) {
            System.out.println("Multiple invariant intersection\n");
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
                System.out.println("Incompatible constant mutations.");
                return;
            }
        }

        //
        // If possible, go back and find an unconditional assignment to the loop variable.
        // We have to be sure that moving this to the for doesn't violate SSA versions.
        //
        Op03SimpleStatement initialValue = findMovableAssignment(statement, loopVariable);
        Assignment initalAssignment = null;

        if (initialValue != null) {
            initalAssignment = (Assignment) initialValue.containedStatement;
            initialValue.nopOut();
        }

        Assignment updateAssignment = (Assignment) baseline.containedStatement;
        for (Op03SimpleStatement incrStatement : mutations) {
            incrStatement.nopOut();
        }
        whileBlockIdentifier.setBlockType(BlockType.FORLOOP);
        whileStatement.replaceWithForLoop(initalAssignment, updateAssignment);
    }

    public static void rewriteWhilesAsFors(List<Op03SimpleStatement> statements) {
        // Find all the while loops beginnings.
        List<Op03SimpleStatement> whileStarts = Functional.filter(statements, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return (in.containedStatement instanceof WhileStatement);
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
                    if (targetStatement.thisComparisonBlock != null) {  // Jumps to the comparison test of a WHILE
                        // Continue loopBlock, IF this statement is INSIDE that block.
                        if (BlockIdentifier.blockIsOneOf(targetStatement.thisComparisonBlock, statement.containedInBlocks)) {
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
//        LinkedList<StructuredStatement> unstructuredStatements = ListFactory.newLinkedList();
        List<Op04StructuredStatement> containers = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            Op04StructuredStatement unstructuredStatement = statement.getStructuredStatementPlaceHolder();
            containers.add(unstructuredStatement);
//            unstructuredStatements.add(unstructuredStatement.getStructuredStatement());
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
            considerAsLoopStart(start, statements, blockIdentifierFactory, blockEndsCache);
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
                        throw new ConfusedCFRException("Invalid back jump. (anti-decompiler?) on " + in.containedStatement);
                    }
                    return true;
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
        do {
            Statement innerStatement = start.getStatement();
            if (innerStatement instanceof IfStatement) {
                return start;
            }
            List<Op03SimpleStatement> targets = start.getTargets();
            if (targets.size() != 1) return null;
            start = targets.get(0);
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


    /* Is the first conditional jump NOT one of the sources of start?
    * Take the target of the first conditional jump - is it somehwhere which is not reachable from
    * any of the forward sources of start without going through start?
    *
    * If so we've probably got a for/while loop.....
    * decode both as a while loop, we can convert it into a for later.
    */
    private static void considerAsLoopStart(final Op03SimpleStatement start, final List<Op03SimpleStatement> statements,
                                            BlockIdentifierFactory blockIdentifierFactory,
                                            Map<BlockIdentifier, Op03SimpleStatement> postBlockCache) {
        final InstrIndex startIndex = start.getIndex();
        System.out.println("Is this a loop start ? " + start);
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
            System.out.println("Can't find a conditional");
            return;
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
                return;
            }
        }

        if (start != conditional) {
            // We'll have problems - there are actions taken inside the conditional.
            return;
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
                return;
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
            return;
        }
        BlockIdentifier blockIdentifier = blockIdentifierFactory.getNextBlockIdentifier(BlockType.WHILELOOP);

        /* Given that the potential statements inside this block are idxConditional+1 -> idxAfterEnd-1, [a->b]
        * there SHOULD be a prefix set (or all) in here which is addressable from idxConditional+1 without leaving the
        * range [a->b].  Determine this.  If we have reachable entries which aren't in the prefix, we can't cope.
        */
        Map<Op03SimpleStatement, Integer> instrToIdx = MapFactory.newMap();
        for (int x = idxConditional + 1; x < idxAfterEnd; ++x) {
            instrToIdx.put(statements.get(x), x);
        }

        Set<Integer> reachableNodes = SetFactory.newSet();
        GraphVisitorReachableInThese graphVisitorCallee = new GraphVisitorReachableInThese(reachableNodes, instrToIdx);
        GraphVisitor<Op03SimpleStatement> visitor = new GraphVisitorDFS<Op03SimpleStatement>(statements.get(idxConditional + 1), graphVisitorCallee);
        visitor.process();

        /* reachable nodes now contains those nodes which are reachable without leaving the range. */

        final int first = idxConditional + 1;
        int last = -1;
        boolean foundLast = false;

        for (int x = first; x < idxAfterEnd; ++x) {
            if (reachableNodes.contains(x)) {
                if (foundLast) {
                    throw new ConfusedCFRException("WHILE loop was exited and re-entered.");
                }
            } else {
                if (!foundLast) last = x - 1;
                foundLast = true;
            }
        }
        if (last == -1) last = idxAfterEnd - 1;

        for (int x = first; x <= last; ++x) {
            statements.get(x).markBlock(blockIdentifier);
        }
        Op03SimpleStatement blockEnd = statements.get(idxAfterEnd);
        start.markPreBlockStatement(blockIdentifier, blockEnd, statements);
        statements.get(first).markFirstStatementInBlock(blockIdentifier);
        blockEnd.markPostBlock(blockIdentifier);
        postBlockCache.put(blockIdentifier, blockEnd);
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
    private static boolean considerAsSimpleIf(Op03SimpleStatement ifStatement, List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory) {
        Op03SimpleStatement takenTarget = ifStatement.targets.get(1);
        Op03SimpleStatement notTakenTarget = ifStatement.targets.get(0);
        int idxTaken = statements.indexOf(takenTarget);
        int idxNotTaken = statements.indexOf(notTakenTarget);
        IfStatement innerIfStatement = (IfStatement) ifStatement.containedStatement;

        int idxCurrent = idxNotTaken;
        if (idxCurrent > idxTaken) return false;

        int idxEnd = idxTaken;
        int maybeElseEndIdx = -1;
        boolean maybeSimpleIfElse = false;
        GotoStatement leaveIfBranchGoto = null;
        List<Op03SimpleStatement> ifBranch = ListFactory.newList();
        List<Op03SimpleStatement> elseBranch = null;
        // Consider the try blocks we're in at this point.  (the ifStatemenet).
        // If we leave any of them, we've left the if.
        List<BlockIdentifier> blocksAtStart = ifStatement.containedInBlocks;
        do {
            Op03SimpleStatement statementCurrent = statements.get(idxCurrent);
            ifBranch.add(statementCurrent);
            JumpType jumpType = statementCurrent.getJumpType();
            if (jumpType.isUnknown()) {
                if (idxCurrent == idxTaken - 1) {
                    Statement mGotoStatement = statementCurrent.containedStatement;
                    if (!(mGotoStatement instanceof GotoStatement)) return false;
                    GotoStatement gotoStatement = (GotoStatement) mGotoStatement;
                    // It's unconditional, and it's a forward jump.
                    Op03SimpleStatement maybeElseEnd = statementCurrent.getTargets().get(0);
                    maybeElseEndIdx = statements.indexOf(maybeElseEnd);
                    if (maybeElseEnd.getIndex().compareTo(takenTarget.getIndex()) <= 0) return false;
                    leaveIfBranchGoto = gotoStatement;
                    maybeSimpleIfElse = true;
                } else {
                    return false;
                }
            }
            idxCurrent++;
        } while (idxCurrent != idxEnd);

        if (maybeSimpleIfElse) {
            // If there is a NO JUMP path between takenTarget and maybeElseEnd, then that's the ELSE block
            elseBranch = ListFactory.newList();
            idxCurrent = idxTaken;
            idxEnd = maybeElseEndIdx;
            do {
                Op03SimpleStatement statementCurrent = statements.get(idxCurrent);
                elseBranch.add(statementCurrent);
                JumpType jumpType = statementCurrent.getJumpType();
                if (jumpType.isUnknown()) return false;
                idxCurrent++;
            } while (idxCurrent != idxEnd);
        }

        Op03SimpleStatement realEnd = statements.get(idxEnd);
        List<BlockIdentifier> blocksAtEnd = realEnd.containedInBlocks;
        if (!(blocksAtStart.containsAll(blocksAtEnd) && blocksAtEnd.size() == blocksAtStart.size())) return false;

        BlockIdentifier ifBlockLabel = blockIdentifierFactory.getNextBlockIdentifier(BlockType.SIMPLE_IF_TAKEN);
        markWholeBlock(ifBranch, ifBlockLabel);
        BlockIdentifier elseBlockLabel = null;
        if (maybeSimpleIfElse) {
            elseBlockLabel = blockIdentifierFactory.getNextBlockIdentifier(BlockType.SIMPLE_IF_ELSE);
            markWholeBlock(elseBranch, elseBlockLabel);
        }

        if (leaveIfBranchGoto != null) leaveIfBranchGoto.setJumpType(JumpType.GOTO_KNOWN);
        innerIfStatement.setJumpType(JumpType.GOTO_KNOWN);
        innerIfStatement.setKnownBlocks(ifBlockLabel, elseBlockLabel);
        return true;
    }

    public static void identifyNonjumpingConditionals(List<Op03SimpleStatement> statements, BlockIdentifierFactory blockIdentifierFactory) {
        boolean success = false;
        do {
            success = false;
            List<Op03SimpleStatement> forwardIfs = Functional.filter(statements, new IsForwardIf());
            for (Op03SimpleStatement forwardIf : forwardIfs) {
                success |= considerAsSimpleIf(forwardIf, statements, blockIdentifierFactory);
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

    @Override
    public String toString() {
        return "Op03SimpleStatement - " + index + " : " + containedStatement;
    }
}
