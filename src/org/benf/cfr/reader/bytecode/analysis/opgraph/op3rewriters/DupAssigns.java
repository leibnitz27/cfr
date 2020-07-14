package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AssignmentExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StackValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.util.collections.Functional;

import java.util.List;

public class DupAssigns {
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
        stm.forceSSAIdentifiers(next.getSSAIdentifiers());
        stm.replaceStatement(new AssignmentSimple(inner1.getLoc(), l1, new AssignmentExpression(BytecodeLoc.NONE, l2, r1)));
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


}
