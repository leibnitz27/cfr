package org.benf.cfr.reader.bytecode.analysis.parse.utils.finalhelp;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.TryStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.DefaultEquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.entities.exceptions.ExceptionTableEntry;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/08/2013
 * Time: 21:50
 */
public class FinallyGraphHelper {
    private final FinallyCatchBody finallyCatchBody;


    public FinallyGraphHelper(FinallyCatchBody finallyCatchBody) {
        this.finallyCatchBody = finallyCatchBody;
    }

    public FinallyCatchBody getFinallyCatchBody() {
        return finallyCatchBody;
    }

    private List<Op03SimpleStatement> filterFalseNegatives(List<Op03SimpleStatement> in) {
        List<Op03SimpleStatement> res = ListFactory.newList();
        for (Op03SimpleStatement i : in) {
            while (i != null && i.getStatement() instanceof Nop) {
                switch (i.getTargets().size()) {
                    case 0:
                        i = null;
                        break;
                    case 1:
                        i = i.getTargets().get(0);
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
            if (i != null) res.add(i);
        }
        return res;
    }

    public Result match(Op03SimpleStatement test) {
        Set<BlockIdentifier> minBlockSet = SetFactory.newOrderedSet(test.getBlockIdentifiers());
        Op03SimpleStatement finalThrowProxy = null;
        Op03SimpleStatement finalThrow = finallyCatchBody.getThrowOp();
        Map<Op03SimpleStatement, Op03SimpleStatement> matched = new IdentityHashMap<Op03SimpleStatement, Op03SimpleStatement>();
        Set<Op03SimpleStatement> toRemove = SetFactory.newOrderedSet();
        LinkedList<Pair> pending = ListFactory.newLinkedList();
        if (finallyCatchBody.isEmpty()) {
            return new Result(toRemove, null, null);
        }
        Pair start = new Pair(test, finallyCatchBody.getCatchCodeStart());
        pending.add(start);
        matched.put(start.b, start.a);

        final EquivalenceConstraint equivalenceConstraint = new FinallyEquivalenceConstraint();

        Set<Op03SimpleStatement> finalThrowProxySources = SetFactory.newOrderedSet();
        while (!pending.isEmpty()) {
            Pair p = pending.removeFirst();
            Op03SimpleStatement a = p.a;
            Op03SimpleStatement b = p.b;
            Statement sa = a.getStatement();
            Statement sb = b.getStatement();
            if (!sa.equivalentUnder(sb, equivalenceConstraint)) {
                return Result.FAIL;
            }

            List<Op03SimpleStatement> tgta = ListFactory.newList(a.getTargets());
            List<Op03SimpleStatement> tgtb = ListFactory.newList(b.getTargets());
            // This fixes 22a, but breaks 1!!
//            tgta.remove(finalThrowProxy);
//            tgtb.remove(finallyCatchBody.throwOp);
            /* Process both, walk no-ops ... walk goto as well? */
            tgta = filterFalseNegatives(tgta);
            tgtb = filterFalseNegatives(tgtb);

            if (tgta.size() != tgtb.size()) {
//                tgta.remove(finalThrowProxy);
//                tgtb.remove(finallyCatchBody.throwOp);
                if (tgta.size() != tgtb.size()) {
                    return Result.FAIL;
                }
            }
            toRemove.add(a);

            for (int x = 0, len = tgta.size(); x < len; ++x) {
                Op03SimpleStatement tgttestx = tgta.get(x);   // test tgt
                Op03SimpleStatement tgthayx = tgtb.get(x); // expected tgt
                /*
                 * We require that it's in at LEAST all the blocks the test started in.
                 */
                Set<BlockIdentifier> newBlockIdentifiers = tgttestx.getBlockIdentifiers();
                if (newBlockIdentifiers.containsAll(minBlockSet)) {
                    if (tgthayx == finalThrow) {
                        if (finalThrowProxy != null && finalThrowProxy != tgttestx) {
                            return Result.FAIL;
                        }
                        finalThrowProxy = tgttestx;
                        finalThrowProxySources.add(a);
                    }
                    if ((!matched.containsKey(tgthayx)) && finallyCatchBody.contains(tgthayx)) {
                        matched.put(tgthayx, tgttestx);
                        pending.add(new Pair(tgttestx, tgthayx));
                    }
                }
            }
        }

        return new Result(toRemove, test, finalThrowProxy);
    }


    private class FinallyEquivalenceConstraint extends DefaultEquivalenceConstraint {
        /*
        * We allow ssa lvalues to mismatch, but they must continue to....
        */
        private final Map<StackSSALabel, StackSSALabel> rhsToLhsMap = MapFactory.newMap();
        private final Map<LocalVariable, LocalVariable> rhsToLhsLVMap = MapFactory.newMap();

        private StackSSALabel mapSSALabel(StackSSALabel s1, StackSSALabel s2) {
            StackSSALabel r1 = rhsToLhsMap.get(s2);
            if (r1 != null) return r1;
            rhsToLhsMap.put(s2, s1);
            return s1;
        }

        private LocalVariable mapLocalVariable(LocalVariable s1, LocalVariable s2) {
            LocalVariable r1 = rhsToLhsLVMap.get(s2);
            if (r1 != null) return r1;
            rhsToLhsLVMap.put(s2, s1);
            return s1;
        }


        @Override
        public boolean equivalent(Object o1, Object o2) {
            if (o1 == null) return o2 == null;
            if (o1 instanceof Collection && o2 instanceof Collection) {
                return equivalent((Collection) o1, (Collection) o2);
            }
            if (o1 instanceof StackSSALabel && o2 instanceof StackSSALabel) {
                o2 = mapSSALabel((StackSSALabel) o1, (StackSSALabel) o2);
            }
            if (o1 instanceof LocalVariable && o2 instanceof LocalVariable) {
                o2 = mapLocalVariable((LocalVariable) o1, (LocalVariable) o2);
            }
            if (o1 instanceof ExceptionTableEntry && o2 instanceof ExceptionTableEntry) {
                return true;
            }
            return super.equivalent(o1, o2);
        }
    }
}
