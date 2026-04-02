package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CatchStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ThrowStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.TryStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

public class RedundantPatternSwitchExceptions {
    /*
     * This is slightly dangerous, because it's possible that matchExceptions are used to signal control flow in an
     * unusual way - but they significantly complicate analysis of record destructuring, as they tend to be scattered around
     * code in a way that doesn't easily admit to generating a clean op04 tree.
     */
    public static List<Op03SimpleStatement> removeRedundantTries(DCCommonState state, List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> tryStarts = Functional.filter(statements, new TypeFilter<TryStatement>(TryStatement.class));
        /*
         * If the try doesn't point at a member of the try, it's been made redundant.
         * Verify that no other references to its' block exist, and remove it.
         * (Verification should be unneccesary)
         */
        boolean effect = false;
        Collections.reverse(tryStarts);
        Map<Op03SimpleStatement, CatchStatement> validCatches = MapFactory.newMap();
        Map<Op03SimpleStatement, List<Op03SimpleStatement>> isMatchException = MapFactory.newLazyMap(new UnaryFunction<Op03SimpleStatement, List<Op03SimpleStatement>>() {
            @Override
            public List<Op03SimpleStatement> invoke(Op03SimpleStatement arg) {
                return ListFactory.newList();
            }
        });
        for (Op03SimpleStatement trys : tryStarts) {
            Statement stm = trys.getStatement();
            if (!(stm instanceof TryStatement)) continue;
            TryStatement tryStatement = (TryStatement) stm;
            BlockIdentifier tryBlock = tryStatement.getBlockIdentifier();
            if (trys.getTargets().size() != 2) continue;
            Op03SimpleStatement cath = trys.getTargets().get(1);
            CatchStatement cst;
            if (validCatches.containsKey(cath)) {
                cst = validCatches.get(cath);
                if (cst == null) continue;
            } else {
                validCatches.put(cath, null);
                if (!(cath.getStatement() instanceof CatchStatement)) continue;
                cst = (CatchStatement)cath.getStatement();
                if (!cst.hasCatchBlockFor(tryBlock)) continue;
                // Cst should have a single statement - throw a match exception.
                List<Op03SimpleStatement> cstt = cath.getTargets();
                if (cstt.size() != 1) continue;
                Statement thr = cstt.get(0).getStatement();
                if (!(thr instanceof ThrowStatement)) continue;
                ThrowStatement thrr = (ThrowStatement)thr;
                Expression e = thrr.getRValue();
                if (!e.getInferredJavaType().getJavaTypeInstance().equals(TypeConstants.MATCH_EXCEPTION)) continue;
                validCatches.put(cath, cst);
            }
            isMatchException.get(cath).add(trys);
        }
        Set<BlockIdentifier> remove = SetFactory.newOrderedSet();
        for (Map.Entry<Op03SimpleStatement, List<Op03SimpleStatement>> entry : isMatchException.entrySet()) {
            Op03SimpleStatement catc = entry.getKey();
            List<Op03SimpleStatement> tries = entry.getValue();
            if (catc.getSources().size() == tries.size()) {
                System.out.println();
                for (Op03SimpleStatement tri : tries) {
                    tri.removeTarget(catc);
                    catc.removeSource(tri);
                    remove.add(((TryStatement)tri.getStatement()).getBlockIdentifier());
                    tri.nopOut();
                }
                catc.nopOut();
            }
        }
        for (Op03SimpleStatement stm: statements) {
            stm.getBlockIdentifiers().removeAll(remove);
        }
        return statements;
    }


}
