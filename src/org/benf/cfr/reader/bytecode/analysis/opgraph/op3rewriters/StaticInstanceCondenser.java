package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StaticFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.List;
import java.util.Map;

public class StaticInstanceCondenser {
    public static final StaticInstanceCondenser INSTANCE = new StaticInstanceCondenser();

    /* Expression statement, which involves a different class (maybe via a field), followed by a static
     * method call.
     * The static method call may be void, or it may be side effecting.
     *
     * i.e.
     *
     * Foo.CONSTANT
     * ConstantType.doThing()
     *
     * Foo.CONSTANT
     * x = ConstantType.doThing()
     */
    public void rewrite(List<Op03SimpleStatement> statements) {
        for (Op03SimpleStatement stm : statements) {
            if (stm.getStatement() instanceof ExpressionStatement) {
                consider(stm);
            }
        }
    }

    private void consider(Op03SimpleStatement stm) {
        ExpressionStatement es = (ExpressionStatement)stm.getStatement();
        Expression e = es.getExpression();
        // Don't think this is relevant for other than lvalue expressions?
        if (!(e instanceof LValueExpression)) return;
        if (stm.getTargets().size() != 1) return;
        JavaTypeInstance typ = e.getInferredJavaType().getJavaTypeInstance();

        Op03SimpleStatement next = Misc.followNopGoto(stm.getTargets().get(0), true, false);

        // Hope to find a static method invokation in next, which invokes a static method on typ.
        Rewriter rewriter = new Rewriter(e, typ);
        next.rewrite(rewriter);
        if (rewriter.success) {
            stm.nopOut();
        }
    }

    private static class Rewriter extends AbstractExpressionRewriter {
        JavaTypeInstance typ;
        Expression object;
        boolean done = false;
        boolean success = false;

        Rewriter(Expression object, JavaTypeInstance typ) {
            this.object = object;
            this.typ = typ;
        }

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (done) return expression;
            if (expression instanceof StaticFunctionInvokation) {
                StaticFunctionInvokation sfe = (StaticFunctionInvokation)expression;
                JavaTypeInstance staticType = sfe.getClazz();
                if (staticType.equals(typ)) {
                    sfe.forceObject(object);
                    success = true;
                }
                done = true;
                return expression;
            }
            return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        }
    }

}
