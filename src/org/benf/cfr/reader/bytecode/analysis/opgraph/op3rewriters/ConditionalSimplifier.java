package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ConditionalSimplifyingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ReturnValueStatement;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.Troolean;

import java.util.List;

class ConditionalSimplifier {
    static void simplifyConditionals(List<Op03SimpleStatement> statements, boolean aggressive, Method method) {
        boolean boolReturn = (method.getMethodPrototype().getReturnType() == RawJavaType.BOOLEAN);
        for (Op03SimpleStatement statement : statements) {
            if (!(statement.getStatement() instanceof IfStatement)) continue;

            // just simplify the condition.
            IfStatement ifStatement = (IfStatement) statement.getStatement();
            ifStatement.simplifyCondition();

            if (boolReturn) {
                replaceEclipseReturn(statement, ifStatement);
            }
        }

        // Fixme - surely simplifyConditional above should be in the rewriter!?
        if (aggressive) {
            ExpressionRewriter conditionalSimplifier = new ConditionalSimplifyingRewriter();
            for (Op03SimpleStatement statement : statements) {
                statement.rewrite(conditionalSimplifier);
            }
        }
    }

    /*
     * Check for
     * if (A) {
     *   return true
     * }
     * return false
     *
     * It's unfortunate that sometimes this is what JDK users explicitly wrote, and previously we'd stand a good chance
     * of recovering it.
     *
     * However, eclipse will generate the above for "return A".
     */
    private static void replaceEclipseReturn(Op03SimpleStatement statement, IfStatement ifStatement) {
        List<Op03SimpleStatement> targets = statement.getTargets();
        if (targets.size() != 2) return;
        Op03SimpleStatement tgt2 = targets.get(0);
        Op03SimpleStatement tgt1 = targets.get(1);
        if (tgt1.getSources().size() != 1 || tgt2.getSources().size() != 1) return;
        Troolean t1 = isBooleanReturn(tgt1.getStatement());
        Troolean t2 = isBooleanReturn(tgt2.getStatement());
        if (t1 == Troolean.NEITHER || t2 == Troolean.NEITHER || t1 == t2) return;
        boolean b2 = t2.boolValue(false);
        ConditionalExpression c = ifStatement.getCondition();
        if (b2) {
            c = c.getNegated().simplify();
        }
        Statement ret = new ReturnValueStatement(BytecodeLoc.TODO, c, RawJavaType.BOOLEAN);
        statement.replaceStatement(ret);
        tgt1.nopOut();
        tgt2.nopOut();
    }

    private static Troolean isBooleanReturn(Statement s) {
        if (!(s instanceof ReturnValueStatement)) return Troolean.NEITHER;
        Expression e = ((ReturnValueStatement) s).getReturnValue();
        if (Literal.TRUE.equals(e)) return Troolean.TRUE;
        if (Literal.FALSE.equals(e)) return Troolean.FALSE;
        return Troolean.NEITHER;
    }
}
