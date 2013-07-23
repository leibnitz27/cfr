package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.BoxingHelper;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import sun.jvm.hotspot.oops.ObjectHeap;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2013
 * Time: 06:26
 * <p/>
 * This seems daft - why do I need to have all this boilerplate?
 * Why not just replace with a cast, and a function pointer.
 */
public class PrimitiveBoxingRewriter implements Op04Rewriter, ExpressionRewriter {


    public PrimitiveBoxingRewriter() {
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        for (StructuredStatement statement : structuredStatements) {
            statement.rewriteExpressions(this);
        }
    }

    @Override
    public void handleStatement(StatementContainer statementContainer) {
        Object statement = statementContainer.getStatement();
        if (statement instanceof BoxingProcessor) {
            ((BoxingProcessor) statement).rewriteBoxing(this);
        }
    }

    /*
     * Expression rewriter boilerplate - note that we can't expect ssaIdentifiers to be non-null.
     */
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof BoxingProcessor) {
            ((BoxingProcessor) expression).rewriteBoxing(this);
        }
        return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof BoxingProcessor) {
            ((BoxingProcessor) expression).rewriteBoxing(this);
        }
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (ConditionalExpression) res;
    }

    @Override
    public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof BoxingProcessor) {
            ((BoxingProcessor) expression).rewriteBoxing(this);
        }
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (AbstractAssignmentExpression) res;
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    @Override
    public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    public Expression sugarAnyBoxing(Expression in) {
        if (in instanceof MemberFunctionInvokation) {
            return BoxingHelper.sugarUnboxing((MemberFunctionInvokation) in);
        }
        if (in instanceof StaticFunctionInvokation) {
            return BoxingHelper.sugarBoxing((StaticFunctionInvokation) in);
        }
        return in;
    }

    public Expression sugarUnboxing(Expression in) {
        if (in instanceof MemberFunctionInvokation) {
            return BoxingHelper.sugarUnboxing((MemberFunctionInvokation) in);
        }
        return in;
    }

    // Cheat, but...
    public boolean isUnboxedType(Expression in) {
        JavaTypeInstance type = in.getInferredJavaType().getJavaTypeInstance();
        if (!(type instanceof RawJavaType)) return false;
        if (in instanceof AbstractFunctionInvokation) return false;
        RawJavaType rawJavaType = (RawJavaType) type;
        return rawJavaType.isUsableType();
    }
}
