package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 17/09/2012
 * Time: 06:43
 */
public class StringBuilderRewriter implements ExpressionRewriter {
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof MemberFunctionInvokation) {
            MemberFunctionInvokation memberFunctionInvokation = (MemberFunctionInvokation) expression;
            if ("toString".equals(memberFunctionInvokation.getName())) {
                Expression lhs = memberFunctionInvokation.getObject();
                Expression result = testAppendChain(lhs);
                if (result != null) return result;
            }
        }
        return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
    }


    @Override
    public void handleStatement(StatementContainer statementContainer) {
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (ConditionalExpression) res;
    }

    @Override
    public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
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

    private Expression testAppendChain(Expression lhs) {
        List<Expression> reverseAppendChain = ListFactory.newList();
        do {
            if (lhs instanceof MemberFunctionInvokation) {
                MemberFunctionInvokation memberFunctionInvokation = (MemberFunctionInvokation) lhs;
                if (memberFunctionInvokation.getName().equals("append") &&
                        memberFunctionInvokation.getArgs().size() == 1) {
                    lhs = memberFunctionInvokation.getObject();
                    reverseAppendChain.add(memberFunctionInvokation.getAppropriatelyCastArgument(0));
                } else {
                    return null;
                }
            } else if (lhs instanceof ConstructorInvokationSimple) {
                ConstructorInvokationSimple newObject = (ConstructorInvokationSimple) lhs;
                String rawName = newObject.getTypeInstance().getRawName();
                if (rawName.equals("java.lang.StringBuilder")) {
                    JavaTypeInstance lastType = reverseAppendChain.get(reverseAppendChain.size() - 1).getInferredJavaType().getJavaTypeInstance();
                    if (lastType instanceof RawJavaType) {
                        return null;
                    }
                    return genStringConcat(reverseAppendChain);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } while (lhs != null);
        return null;
    }

    private Expression genStringConcat(List<Expression> revList) {
        int x = revList.size() - 1;
        if (x < 0) return null;
        Expression head = revList.get(x);
        InferredJavaType inferredJavaType = new InferredJavaType(TypeConstants.STRING, InferredJavaType.Source.STRING_TRANSFORM, true);
        for (--x; x >= 0; --x) {
            head = new ArithmeticOperation(inferredJavaType, head, revList.get(x), ArithOp.PLUS);
        }
        return head;
    }
}
