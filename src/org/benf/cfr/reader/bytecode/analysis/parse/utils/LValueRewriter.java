package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface LValueRewriter<T> {
    Expression getLValueReplacement(LValue lValue, SSAIdentifiers<LValue> ssaIdentifiers, StatementContainer<T> statementContainer);

    boolean explicitlyReplaceThisLValue(LValue lValue);

    void checkPostConditions(LValue lValue, Expression rValue);

    LValueRewriter getWithFixed(Set<SSAIdent> fixed);

    boolean needLR();

    LValueRewriter keepConstant(Collection<LValue> usedLValues);

    class Util {
        public static void rewriteArgArray(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, List<Expression> args) {
            boolean lr = lValueRewriter.needLR();
            int argsSize = args.size();
            for (int x = 0; x < argsSize; ++x) {
                int y = lr ? x : argsSize - 1 - x;
                args.set(y, args.get(y).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
            }
        }
    }
}
