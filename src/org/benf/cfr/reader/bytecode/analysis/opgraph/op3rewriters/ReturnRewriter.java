package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;

import java.util.List;

public class ReturnRewriter {
    private static void replaceReturningIf(Op03SimpleStatement ifStatement, boolean aggressive) {
        if (!(ifStatement.getStatement().getClass() == IfStatement.class)) return;
        IfStatement innerIf = (IfStatement) ifStatement.getStatement();
        Op03SimpleStatement tgt = ifStatement.getTargets().get(1);
        final Op03SimpleStatement origtgt = tgt;
        boolean requireJustOneSource = !aggressive;
        do {
            Op03SimpleStatement next = Misc.followNopGoto(tgt, requireJustOneSource, aggressive);
            if (next == tgt) break;
            tgt = next;
        } while (true);
        Statement tgtStatement = tgt.getStatement();
        if (tgtStatement instanceof ReturnStatement) {
            ifStatement.replaceStatement(new IfExitingStatement(innerIf.getCondition(), tgtStatement));
        } else {
            return;
        }
        origtgt.removeSource(ifStatement);
        ifStatement.removeTarget(origtgt);
    }

    public static void replaceReturningIfs(List<Op03SimpleStatement> statements, boolean aggressive) {
        List<Op03SimpleStatement> ifStatements = Functional.filter(statements, new TypeFilter<IfStatement>(IfStatement.class));
        for (Op03SimpleStatement ifStatement : ifStatements) {
            replaceReturningIf(ifStatement, aggressive);
        }
    }

    public static void propagateToReturn2(List<Op03SimpleStatement> statements) {
        boolean success = false;
        for (Op03SimpleStatement stm : statements) {
            Statement inner = stm.getStatement();

            if (inner instanceof ReturnStatement) {
                /*
                 * Another very aggressive operation - find any goto which directly jumps to a return, and
                 * place a copy of the return in the goto.
                 *
                 * This will interfere with returning a ternary, however because it's an aggressive option, it
                 * won't be used unless needed.
                 *
                 * We look for returns rather than gotos, as returns are less common.
                 */
                success |= pushReturnBack(stm);
            }
        }
        if (success) Op03Rewriters.replaceReturningIfs(statements, true);
    }

    private static boolean pushReturnBack(final Op03SimpleStatement returnStm) {

        ReturnStatement returnStatement = (ReturnStatement) returnStm.getStatement();
        final List<Op03SimpleStatement> replaceWithReturn = ListFactory.newList();

        new GraphVisitorDFS<Op03SimpleStatement>(returnStm.getSources(), new BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>>() {
            @Override
            public void call(Op03SimpleStatement arg1, GraphVisitor<Op03SimpleStatement> arg2) {
                Class<?> clazz = arg1.getStatement().getClass();
                if (clazz == CommentStatement.class ||
                        clazz == Nop.class ||
                        clazz == DoStatement.class) {
                    arg2.enqueue(arg1.getSources());
                } else if (clazz == WhileStatement.class) {
                    // only if it's 'while true'.
                    WhileStatement whileStatement = (WhileStatement)arg1.getStatement();
                    if (whileStatement.getCondition() == null) {
                        arg2.enqueue(arg1.getSources());
                        replaceWithReturn.add(arg1);
                    }
                } else if (clazz == GotoStatement.class) {
                    arg2.enqueue(arg1.getSources());
                    replaceWithReturn.add(arg1);
                }
            }
        }).process();

        if (replaceWithReturn.isEmpty()) return false;

        CloneHelper cloneHelper = new CloneHelper();
        for (Op03SimpleStatement remove : replaceWithReturn) {
            remove.replaceStatement(returnStatement.deepClone(cloneHelper));
            for (Op03SimpleStatement tgt : remove.getTargets()) {
                tgt.removeSource(remove);
            }
            remove.clearTargets();
        }

        return true;
    }


}
