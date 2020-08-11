package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.util.collections.ListFactory;

import java.util.List;

public class NegativeJumps {
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

                if (zStatement.getSources().size() != 1) {
                    continue;
                }

                if (aStatement.getTargets().get(0) == zStatement &&
                        aStatement.getTargets().get(1) == xStatement) {

                    Statement innerZStatement = zStatement.getStatement();
                    if (innerZStatement.getClass() == GotoStatement.class) {
                        // Yep, this is it.
                        Op03SimpleStatement yStatement = zStatement.getTargets().get(0);

                        if (yStatement == zStatement) continue;

                        // Order is important.
                        aStatement.replaceTarget(xStatement, yStatement);
                        aStatement.replaceTarget(zStatement, xStatement);

                        yStatement.replaceSource(zStatement, aStatement);
                        zStatement.getSources().clear();
                        zStatement.getTargets().clear();
                        zStatement.replaceStatement(new Nop());
                        removeThese.add(zStatement);

                        IfStatement innerAIfStatement = (IfStatement) innerAStatement;
                        innerAIfStatement.negateCondition();
                    }
                }
            }
        }
        statements.removeAll(removeThese);
    }

}
