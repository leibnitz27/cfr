package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.ExpressionReplacingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Misc {
    public static void flattenCompoundStatements(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> newStatements = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            if (statement.isCompound()) {
                newStatements.addAll(statement.splitCompound());
            }
        }
        statements.addAll(newStatements);
    }

    public static Op03SimpleStatement getLastInRangeByIndex(Set<Op03SimpleStatement> stms) {
        List<Op03SimpleStatement> lst = ListFactory.newList(stms);
        Collections.sort(lst, new CompareByIndex(false));
        return lst.get(0);
    }

    public static Op03SimpleStatement skipComments(Op03SimpleStatement stm) {
        while (stm.getStatement() instanceof CommentStatement) {
            List<Op03SimpleStatement> targets = stm.getTargets();
            if (targets.size() != 1) {
                return stm;
            }
            stm = targets.get(0);
        }
        return stm;
    }

    public static class IsForwardJumpTo implements Predicate<Op03SimpleStatement> {
        private final InstrIndex thisIndex;

        public IsForwardJumpTo(InstrIndex thisIndex) {
            this.thisIndex = thisIndex;
        }

        @Override
        public boolean test(Op03SimpleStatement in) {
            return thisIndex.isBackJumpTo(in);
        }
    }


    public static class IsBackJumpTo implements Predicate<Op03SimpleStatement> {
        private final InstrIndex thisIndex;

        IsBackJumpTo(InstrIndex thisIndex) {
            this.thisIndex = thisIndex;
        }

        @Override
        public boolean test(Op03SimpleStatement in) {
            return thisIndex.isBackJumpFrom(in);
        }
    }

    public static class HasBackJump implements Predicate<Op03SimpleStatement> {
        @Override
        public boolean test(Op03SimpleStatement in) {
            InstrIndex inIndex = in.getIndex();
            List<Op03SimpleStatement> targets = in.getTargets();
            for (Op03SimpleStatement target : targets) {
                if (target.getIndex().compareTo(inIndex) <= 0) {
                    Statement statement = in.getStatement();
                    if (!(statement instanceof JumpingStatement)) {
                        if (statement instanceof JSRRetStatement ||
                                statement instanceof WhileStatement) {
                            return false;
                        }
                        throw new ConfusedCFRException("Invalid back jump on " + statement);
                    } else {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public static class GetBackJump implements UnaryFunction<Op03SimpleStatement, Op03SimpleStatement> {
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


    private static class GraphVisitorReachableInThese implements BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>> {
        private final Set<Integer> reachable;
        private final Map<Op03SimpleStatement, Integer> instrToIdx;

        GraphVisitorReachableInThese(Set<Integer> reachable, Map<Op03SimpleStatement, Integer> instrToIdx) {
            this.reachable = reachable;
            this.instrToIdx = instrToIdx;
        }

        @Override
        public void call(Op03SimpleStatement node, GraphVisitor<Op03SimpleStatement> graphVisitor) {
            Integer idx = instrToIdx.get(node);
            if (idx == null) return;
            reachable.add(idx);
            for (Op03SimpleStatement target : node.getTargets()) {
                graphVisitor.enqueue(target);
            }
        }
    }


    static int getFarthestReachableInRange(List<Op03SimpleStatement> statements, int start, int afterEnd) {
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
            if (reachableNodes.contains(x) || statements.get(x).isAgreedNop()) {
                if (foundLast) {
                    // This is 'failure' behaviour.  It will probably trigger a re-sort.
                    // TODO : Handle better.
                    return last;
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

    static Set<Op03SimpleStatement> followNopGotoBackwards(Op03SimpleStatement eventualtarget) {

        final Set<Op03SimpleStatement> result = SetFactory.newSet();

        new GraphVisitorDFS<Op03SimpleStatement>(eventualtarget, new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
            @Override
            public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                for (Op03SimpleStatement source : arg1.getSources()) {
                    Statement statement = source.getStatement();
                    Class clazz = statement.getClass();
                    if (clazz == Nop.class ||
                        clazz == CaseStatement.class) {
                        arg2.enqueue(source);
                    } else if (clazz == GotoStatement.class) {
                        result.add(source);
                        arg2.enqueue(source);
                    }
                    // It would be great to enable this, however it causes issues with dex2jar source.
/*
                    } else if (clazz == IfStatement.class) {
                        if (source.getTargets().size() == 2 &&
                            source.getTargets().get(1) == arg1) {
                            result.add(source);
                        }
                        */
                }
            }
        }).process();

        return result;
    }

    // Should have a set to make sure we've not looped.
    static Op03SimpleStatement followNopGoto(Op03SimpleStatement in, boolean requireJustOneSource, boolean aggressive) {
        if (in == null) {
            return null;
        }
        if (requireJustOneSource && in.getSources().size() != 1) return in;
        if (in.getTargets().size() != 1) return in;
        Statement statement = in.getStatement();
        if (statement instanceof Nop ||
                statement instanceof GotoStatement ||
                (aggressive && statement instanceof CaseStatement) ||
                (aggressive && statement instanceof MonitorExitStatement)) {

            in = in.getTargets().get(0);
        }
        return in;
    }

    public static Op03SimpleStatement followNopGotoChain(Op03SimpleStatement in, boolean requireJustOneSource, boolean skipLabels) {
        return followNopGotoChainUntil(in, null, requireJustOneSource, skipLabels);
    }

    public static Op03SimpleStatement followNopGotoChainUntil(Op03SimpleStatement in, Op03SimpleStatement until, boolean requireJustOneSource, boolean skipLabels) {
        if (in == null) return null;
        Set<Op03SimpleStatement> seen = SetFactory.newSet();
        if (until != null) {
            seen.add(until);
        }
        do {
            if (!seen.add(in)) return in;
            Op03SimpleStatement next = followNopGoto(in, requireJustOneSource, skipLabels);
            if (next == in) return in;
            in = next;
        } while (true);
    }

    static void markWholeBlock(List<Op03SimpleStatement> statements, BlockIdentifier blockIdentifier) {
        Op03SimpleStatement start = statements.get(0);
        start.markFirstStatementInBlock(blockIdentifier);
        for (Op03SimpleStatement statement : statements) {
            statement.markBlock(blockIdentifier);
        }
    }

    static boolean findHiddenIter(Statement statement, LValue lValue, Expression rValue, Set<Expression> poison) {
        AssignmentExpression needle = new AssignmentExpression(statement.getLoc(), lValue, rValue);
        NOPSearchingExpressionRewriter finder = new NOPSearchingExpressionRewriter(needle, poison);

        statement.rewriteExpressions(finder, statement.getContainer().getSSAIdentifiers());
        return finder.isFound();
    }

    static void replaceHiddenIter(Statement statement, LValue lValue, Expression rValue) {
        AssignmentExpression needle = new AssignmentExpression(statement.getLoc(), lValue, rValue);
        ExpressionReplacingRewriter finder = new ExpressionReplacingRewriter(needle, new LValueExpression(lValue));

        statement.rewriteExpressions(finder, statement.getContainer().getSSAIdentifiers());
    }

    static Op03SimpleStatement findSingleBackSource(Op03SimpleStatement start) {
        List<Op03SimpleStatement> startSources = Functional.filter(start.getSources(), new IsForwardJumpTo(start.getIndex()));
        if (startSources.size() != 1) {
            return null;
        }
        return startSources.get(0);
    }

    static BlockIdentifier findOuterBlock(BlockIdentifier b1, BlockIdentifier b2, List<Op03SimpleStatement> statements) {
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


    public static class GraphVisitorBlockReachable implements BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>> {

        private final Op03SimpleStatement start;
        private final BlockIdentifier blockIdentifier;
        private final Set<Op03SimpleStatement> found = SetFactory.newSet();
        private final Set<Op03SimpleStatement> exits = SetFactory.newSet();

        private GraphVisitorBlockReachable(Op03SimpleStatement start, BlockIdentifier blockIdentifier) {
            this.start = start;
            this.blockIdentifier = blockIdentifier;
        }

        @Override
        public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
            if (arg1 == start || arg1.getBlockIdentifiers().contains(blockIdentifier)) {
                found.add(arg1);
                for (Op03SimpleStatement target : arg1.getTargets()) arg2.enqueue(target);
            } else {
                exits.add(arg1);
            }
        }

        private Set<Op03SimpleStatement> privGetBlockReachable() {
            GraphVisitorDFS<Op03SimpleStatement> reachableInBlock = new GraphVisitorDFS<Op03SimpleStatement>(
                    start,
                    this
            );
            reachableInBlock.process();
            return found;
        }

        static Set<Op03SimpleStatement> getBlockReachable(Op03SimpleStatement start, BlockIdentifier blockIdentifier) {
            GraphVisitorBlockReachable r = new GraphVisitorBlockReachable(start, blockIdentifier);
            return r.privGetBlockReachable();
        }

        private Pair<Set<Op03SimpleStatement>, Set<Op03SimpleStatement>> privGetBlockReachableAndExits() {
            GraphVisitorDFS<Op03SimpleStatement> reachableInBlock = new GraphVisitorDFS<Op03SimpleStatement>(
                    start,
                    this
            );
            reachableInBlock.process();
            return Pair.make(found,exits);
        }

        static Pair<Set<Op03SimpleStatement>, Set<Op03SimpleStatement>> getBlockReachableAndExits(Op03SimpleStatement start, BlockIdentifier blockIdentifier) {
            GraphVisitorBlockReachable r = new GraphVisitorBlockReachable(start, blockIdentifier);
            return r.privGetBlockReachableAndExits();
        }
    }

    static Set<Op03SimpleStatement> collectAllSources(Collection<Op03SimpleStatement> statements) {
        Set<Op03SimpleStatement> result = SetFactory.newSet();
        for (Op03SimpleStatement statement : statements) {
            result.addAll(statement.getSources());
        }
        return result;
    }

    public static boolean justReachableFrom(Op03SimpleStatement target, Op03SimpleStatement maybeSource, int checkDepth) {
        while (target != maybeSource && checkDepth-- > 0) {
            List<Op03SimpleStatement> sources = target.getSources();
            if (sources.size() != 1) return false;
            target = sources.get(0);
        }
        return target == maybeSource;
    }
}
