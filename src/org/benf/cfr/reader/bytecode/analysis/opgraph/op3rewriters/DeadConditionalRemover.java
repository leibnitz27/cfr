package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.List;
import java.util.Map;

public class DeadConditionalRemover {
    public static final DeadConditionalRemover INSTANCE = new DeadConditionalRemover();

    public List<Op03SimpleStatement> rewrite(List<Op03SimpleStatement> statements) {
        boolean effect = false;
        for (Op03SimpleStatement stm : statements) {
            if (stm.getStatement() instanceof IfStatement) {
                if (rewrite(stm)) effect = true;
            }
        }
        if (effect) {
           return Cleaner.removeUnreachableCode(statements, false);
        }
        return statements;
    }

    private boolean rewrite(Op03SimpleStatement stm) {
        IfStatement ifs = (IfStatement) stm.getStatement();
        // We explicitly state that we don't know the value of any lvalues
        // on the way in, and if any are set, we don't want this.
        Map<LValue, Literal> effects = MapFactory.newMap();
        Literal val = ifs.getCondition().getComputedLiteral(effects);
        if (val == null || !effects.isEmpty()) return false;
        Op03SimpleStatement removeTarget = null;
        Statement replacement = null;
        if (Literal.TRUE.equals(val)) {
            removeTarget = stm.getTargets().get(0);
            // would have required a jump, so goto.
            replacement = new GotoStatement();
        } else if (Literal.FALSE.equals(val)) {
            removeTarget = stm.getTargets().get(1);
            // This would have fallen through, so nop.
            replacement = new Nop();
        }
        if (removeTarget == null) return false;
        removeTarget.removeSource(stm);
        stm.replaceStatement(replacement);
        stm.removeGotoTarget(removeTarget);
        return true;
    }
}
