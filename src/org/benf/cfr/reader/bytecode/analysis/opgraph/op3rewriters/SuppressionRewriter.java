package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.BytecodeMeta;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.types.BindingSuperContainer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.ListFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/*
 * Detect the pattern which is EXACTLY
 *
 * if (y == null) goto B
 * try {
 *   foo.close()
 *   goto A
 * } catch (Throwable x) (
 *   y.addSuppressed(x)
 *   goto A
 * }
 * B:
 * foo.close();
 * A:
 *
 * This is more specific than we need it to be but will catch a lot of cases.
 *
 * Replaced with
 *
 * foo.close() [marked as Suppressed failure]
 */
public class SuppressionRewriter {
    public static List<Op03SimpleStatement> rewrite(List<Op03SimpleStatement> statements, BytecodeMeta bytecodeMeta) {
        if (!bytecodeMeta.has(BytecodeMeta.CodeInfoFlag.USES_EXCEPTIONS)) return statements;
        // we're going to need to make linear code assumptions - indexed search.
        boolean acted = false;
        for (int x=statements.size()-1;x>=0;--x) {
            Op03SimpleStatement stm = statements.get(x);
            if (stm.getStatement() instanceof TryStatement) {
                acted |= testSuppressedClose(stm, x, statements);
            }
        }
        if (acted) {
            statements = Cleaner.removeUnreachableCode(statements, true);
        }
        return statements;
    }

    private static boolean testSuppressedClose(Op03SimpleStatement tryStm, int idx, List<Op03SimpleStatement> lst) {

        if (tryStm.getSources().size() != 1) return false;

        List<Op03SimpleStatement> tryTargets = tryStm.getTargets();
        if (tryTargets.size() != 2) return false;

        Op03SimpleStatement testClose = lst.get(idx+1);
        if (testClose != tryTargets.get(0)) return false;

        WildcardMatch wcm = new WildcardMatch();
        Statement testCloseStatement = new ExpressionStatement(wcm.getMemberFunction("close", "close", new LValueExpression(wcm.getLValueWildCard("object"))));
        if (!testCloseStatement.equals(testClose.getStatement())) return false;

        LValue object = wcm.getLValueWildCard("object").getMatch();
        if (!(object instanceof LocalVariable)) return false;
        BindingSuperContainer bindingSuperContainer = object.getInferredJavaType().getJavaTypeInstance().getBindingSupers();
        if (bindingSuperContainer == null) return false;
        if (!bindingSuperContainer.containsBase(TypeConstants.CLOSEABLE)) return false;

        // We're calling close on a closeable.  Is the next instruction a goto, to the same target as the end of the catch?
        List<Op03SimpleStatement> tgt2 = testClose.getTargets();
        Op03SimpleStatement testGoto = lst.get(idx+2);
        if (testGoto != tgt2.get(0)) return false;

        Op03SimpleStatement testCatch = lst.get(idx+3);
        if (testCatch != tryTargets.get(1)) return false;

        if (!(testCatch.getStatement() instanceof CatchStatement)) return false;
        CatchStatement catchStatement = (CatchStatement)testCatch.getStatement();
        LValue caught = catchStatement.getCreatedLValue();

        Op03SimpleStatement testSuppressed = lst.get(idx + 4);
        if (testSuppressed != testCatch.getTargets().get(0)) return false;

        Statement suppression = new ExpressionStatement(
                wcm.getMemberFunction("suppress", "addSuppressed", false,
                        new LValueExpression(wcm.getLValueWildCard("suppressor")),
                        ListFactory.<Expression>newList(
                                new CastExpression(new InferredJavaType(TypeConstants.THROWABLE, InferredJavaType.Source.EXPRESSION), new LValueExpression(caught))
                        )
                )
        );
        if (!suppression.equals(testSuppressed.getStatement())) return false;

        Op03SimpleStatement testGoto2 = lst.get(idx+5);
        if (testGoto2 != testSuppressed.getTargets().get(0)) return false;
        if (!testGoto2.getStatement().equals(testGoto.getStatement())) return false;
        Op03SimpleStatement testGotoTarget = testGoto.getTargets().get(0);

        /*
         * Now, expect the statement AFTER this to be the same as the close statement, target of the pretry goto, and
         * to fall through to the testGoto target!
         */
        Op03SimpleStatement secondClose = lst.get(idx + 6);
        if (!secondClose.getStatement().equals(testClose.getStatement())) return false;

        if (!secondClose.getTargets().get(0).equals(testGotoTarget)) return false;
        if (lst.get(idx+7) != testGotoTarget) return false;

        /* Now check if the statement BEFORE the try is correct */
        Op03SimpleStatement testIf = lst.get(idx-1);
        Statement testIfStatement = new IfStatement(new ComparisonOperation(new LValueExpression(wcm.getLValueWildCard("suppressor")), Literal.NULL, CompOp.EQ));
        if (!testIfStatement.equals(testIf.getStatement())) return false;
        List<Op03SimpleStatement> testIfTargets = testIf.getTargets();
        if (testIfTargets.get(0) != tryStm) return false;
        if (testIfTargets.get(1) != secondClose) return false;
        if (secondClose.getSources().size() != 1) return false;

        /*
         * Ok! We've matched this ridiculously specific pattern.  Nop it all out.
         * (we really need to keep the close).
         */

        List<Op03SimpleStatement> sources = testIf.getSources();
        testGotoTarget.removeSource(testGoto);
        testGotoTarget.removeSource(testGoto2);
        for (Op03SimpleStatement source: sources) {
            source.replaceTarget(testIf, secondClose);
            secondClose.addSource(source);
        }


        return true;
    }
}
