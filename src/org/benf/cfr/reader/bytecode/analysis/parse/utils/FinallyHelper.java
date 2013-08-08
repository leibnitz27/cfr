package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CatchStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.TryStatement;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 05/08/2013
 * Time: 06:36
 */
public class FinallyHelper {
    private final Op03SimpleStatement finallyStart;
    private final BlockIdentifier finallyIdent;
    private final Op03SimpleStatement finalThrow;
    private final Map<Op03SimpleStatement, Result> cachedResults = MapFactory.newMap();
    private final Op03SimpleStatement guessedFinalCatchBlock;

    public FinallyHelper(Op03SimpleStatement finallyStart, BlockIdentifier finallyIdent, Op03SimpleStatement finalThrow, Op03SimpleStatement guessedFinallyCatchBlock) {
        this.finallyStart = finallyStart;
        this.finallyIdent = finallyIdent;
        this.finalThrow = finalThrow;
        this.guessedFinalCatchBlock = guessedFinallyCatchBlock;
    }

    public void markAsTestedFrom(Op03SimpleStatement possibleFinallyBlock, Op03SimpleStatement source) {
    }

    public Result testEquivalent(Op03SimpleStatement possibleFinallyBlock, TryStatement tryStatement) {
        Result cachedResult = cachedResults.get(possibleFinallyBlock);
        if (cachedResult != null) return cachedResult;
        FinallyGraphHelper finallyGraphHelper = new FinallyGraphHelper(new Pair(finallyStart, possibleFinallyBlock), finallyIdent, finalThrow, tryStatement);
        Result res = finallyGraphHelper.match();
        cachedResults.put(possibleFinallyBlock, res);
        return res;
    }

    private static class Pair {
        public final Op03SimpleStatement a;
        public final Op03SimpleStatement b;

        public Pair(Op03SimpleStatement a, Op03SimpleStatement b) {
            this.a = a;
            this.b = b;
        }
    }

    private static class FinallyGraphHelper {
        private final Pair start;
        private final BlockIdentifier ident;
        private final Op03SimpleStatement finalThrow;
        private final TryStatement tryStatement;
        private final EquivalenceConstraint equivalenceConstraint = new FinallyEquivalenceConstraint();

        /*
         * We allow ssa lvalues to mismatch, but they must continue to....
         */
        private final Map<StackSSALabel, StackSSALabel> rhsToLhsMap = MapFactory.newMap();

        public FinallyGraphHelper(Pair start, BlockIdentifier ident, Op03SimpleStatement finalThrow, TryStatement tryStatement) {
            this.start = start;
            this.ident = ident;
            this.finalThrow = finalThrow;
            this.tryStatement = tryStatement;
        }

        public Result match() {

            Op03SimpleStatement finalThrowProxy = null;
            Map<Op03SimpleStatement, Op03SimpleStatement> matched = new IdentityHashMap<Op03SimpleStatement, Op03SimpleStatement>();
            Set<Op03SimpleStatement> toRemove = SetFactory.newSet();
            LinkedList<Pair> pending = ListFactory.newLinkedList();
            pending.add(start);
            matched.put(start.a, start.b);

            Set<Op03SimpleStatement> finalThrowProxySources = SetFactory.newSet();
            while (!pending.isEmpty()) {
                Pair p = pending.removeFirst();
                Op03SimpleStatement a = p.a;
                Op03SimpleStatement b = p.b;
                Statement sa = a.getStatement();
                Statement sb = b.getStatement();
                if (!sa.equivalentUnder(sb, equivalenceConstraint)) {
                    return Result.FAIL;
                }

                List<Op03SimpleStatement> tgta = a.getTargets();
                List<Op03SimpleStatement> tgtb = b.getTargets();
                if (tgta.size() != tgtb.size()) {
                    return Result.FAIL;
                }
                toRemove.add(b);

                for (int x = 0, len = tgta.size(); x < len; ++x) {
                    Op03SimpleStatement tgtax = tgta.get(x);
                    if (tgtax.getBlockIdentifiers().contains(ident) && !(tgtax == finalThrow)) {
                        if (!matched.containsKey(tgtax)) {
                            Op03SimpleStatement tgtbx = tgtb.get(x);
                            Pair next = new Pair(tgtax, tgtbx);
                            pending.add(next);
                            matched.put(tgtax, tgtbx);
                        }
                    }
                    if (tgtax == finalThrow) {
                        finalThrowProxySources.add(b);
                        finalThrowProxy = tgtb.get(x);
                    }
                }
            }

            return new Result(true, start.b, finalThrowProxy, finalThrowProxySources, toRemove, tryStatement);
        }

        private StackSSALabel mapSSALabel(StackSSALabel s1, StackSSALabel s2) {
            StackSSALabel r1 = rhsToLhsMap.get(s2);
            if (r1 != null) return r1;
            rhsToLhsMap.put(s2, s1);
            return s1;
        }

        private class FinallyEquivalenceConstraint extends DefaultEquivalenceConstraint {
            @Override
            public boolean equivalent(Object o1, Object o2) {
                if (o1 == null) return o2 == null;
                if (o1 instanceof StackSSALabel && o2 instanceof StackSSALabel) {
                    o2 = mapSSALabel((StackSSALabel) o1, (StackSSALabel) o2);
                }
                return super.equivalent(o1, o2);
            }
        }
    }

    public static class Result {
        private final boolean matched;
        private final Op03SimpleStatement startOfFinallyCopy;
        private final Op03SimpleStatement finalThrowRedirect;
        private final Set<Op03SimpleStatement> toRemove;
        private final Set<Op03SimpleStatement> finalThrowProxySources;
        private final TryStatement tryStatement;


        public static Result FAIL = new Result();

        public Result(boolean matched, Op03SimpleStatement startOfFinallyCopy, Op03SimpleStatement finalThrowRedirect,
                      Set<Op03SimpleStatement> finalThrowProxySources, Set<Op03SimpleStatement> toRemove,
                      TryStatement tryStatement) {
            this.matched = matched;
            this.startOfFinallyCopy = startOfFinallyCopy;
            this.finalThrowRedirect = finalThrowRedirect;
            this.toRemove = toRemove;
            this.finalThrowProxySources = finalThrowProxySources;
            this.tryStatement = tryStatement;
        }

        private Result() {
            this.matched = false;
            this.finalThrowRedirect = null;
            this.toRemove = null;
            this.startOfFinallyCopy = null;
            this.finalThrowProxySources = null;
            this.tryStatement = null;
        }

        public boolean isMatched() {
            return matched;
        }

        public Op03SimpleStatement getFinalThrowRedirect() {
            return finalThrowRedirect;
        }

        public Set<Op03SimpleStatement> getToRemove() {
            return toRemove;
        }

        public Op03SimpleStatement getStartOfFinallyCopy() {
            return startOfFinallyCopy;
        }

        public Set<Op03SimpleStatement> getFinalThrowProxySources() {
            return finalThrowProxySources;
        }

        public TryStatement getTryStatement() {
            return tryStatement;
        }
    }


    public void linkPeerTries(Set<Op03SimpleStatement> tryStarts, List<Op03SimpleStatement> allStatements) {
        Set<BlockIdentifier> tryBlocks = SetFactory.newSet(Functional.map(tryStarts, new UnaryFunction<Op03SimpleStatement, BlockIdentifier>() {
            @Override
            public BlockIdentifier invoke(Op03SimpleStatement arg) {
                return ((TryStatement) arg.getStatement()).getBlockIdentifier();
            }
        }));

        for (Op03SimpleStatement tri : tryStarts) {
            TryStatement thisTry = (TryStatement) tri.getStatement();
            List<Op03SimpleStatement> sources = tri.getSources();
            BlockIdentifier thisblock = thisTry.getBlockIdentifier();
            if (sources.size() == 1) {
                Set<BlockIdentifier> callerBlocks = sources.get(0).getBlockIdentifiers();
                Set<BlockIdentifier> wanted = SetFactory.newSet(tryBlocks);
                wanted.remove(thisblock);
                wanted.retainAll(callerBlocks);
                if (wanted.size() == 1) {
                    // Replace thisBlock with wanted.get
                    BlockIdentifier aggregateWith = wanted.iterator().next();
                    Op03SimpleStatement thisTryStm = (Op03SimpleStatement) thisTry.getContainer();
                    List<Op03SimpleStatement> thisTriTargets = ListFactory.newList(thisTryStm.getTargets());
                    for (int x = 1, len = thisTriTargets.size(); x < len; ++x) {
                        Op03SimpleStatement tgt = thisTriTargets.get(x);
                        thisTryStm.removeTarget(tgt);
                        tgt.removeSource(thisTryStm);
                        /* And remove this try from the catch statement tgt */
                        Statement shouldBeCatch = tgt.getStatement();
                        if (shouldBeCatch instanceof CatchStatement) {
                            CatchStatement catchStatement = (CatchStatement) shouldBeCatch;
                            catchStatement.removeCatchBlockFor(thisblock);
                        }
                    }
                    thisTry.getContainer().nopOut();
                    for (Op03SimpleStatement stm : allStatements) {
                        if (stm.getBlockIdentifiers().remove(thisblock)) {
                            stm.getBlockIdentifiers().add(aggregateWith);
                        }
                    }
                    int x = 1;
                }
            }
        }
    }

    public void unlinkTries(Set<Op03SimpleStatement> wasTries) {
        for (Op03SimpleStatement wasTry : wasTries) {
            Statement statement = wasTry.getStatement();
            if (statement instanceof TryStatement) {
                wasTry.removeTarget(guessedFinalCatchBlock);
                guessedFinalCatchBlock.removeSource(wasTry);
            }
        }
    }
}
