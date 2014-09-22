package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StaticFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.GenericTypeBinder;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericBaseInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;

import java.util.List;

public class ExplicitTypeCallRewriter extends AbstractExpressionRewriter {

    private final InnerExplicitTypeCallRewriter inner = new InnerExplicitTypeCallRewriter();

    public ExplicitTypeCallRewriter() {
    }

    private class InnerExplicitTypeCallRewriter extends AbstractExpressionRewriter {
        // If the signature of the function we're calling is generic, but there's insufficient generic
        // information in the arguments, then we should give the call an explicit type.
        //
        // i.e. ListFactory.newList() --> ListFactory.<Integer>newList()
        //
        private Expression rewriteFunctionInvokation(AbstractFunctionInvokation invokation) {
            if (invokation instanceof StaticFunctionInvokation) {
                MethodPrototype p = ((StaticFunctionInvokation) invokation).getFunction().getMethodPrototype();
                // Does p have any generics?  Are they satisfied by the arguments?
                // Let's take the easy one first - does it have formal parameters, yet no arguments?
                if (p.hasFormalTypeParameters() && p.getVisibleArgCount() == 0) {
                    // Can we use the known RETURN type, to improve the call?
                    JavaTypeInstance returnType = p.getReturnType();
                    if (returnType instanceof JavaGenericBaseInstance) {
                        JavaTypeInstance usedType = invokation.getInferredJavaType().getJavaTypeInstance();
                        GenericTypeBinder binder = GenericTypeBinder.extractBaseBindings((JavaGenericBaseInstance) returnType, usedType);
                        if (binder != null) {
                            List<JavaTypeInstance> types = p.getExplicitGenericUsage(binder);
                            /*
                             * We set these even if they're going to be discarded as too unbound by the illegalGenericRewriter
                             * later.
                             */
                            invokation.setExplicitGenerics(types);
                        }
                    }
                }
            }
            return invokation;
        }

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (expression instanceof AbstractFunctionInvokation) {
                // We need to look at any argument of this function / return, and see if they need to be rewritten.
                expression = rewriteFunctionInvokation((AbstractFunctionInvokation) expression);
            }
            // Note - we don't call super - we don't recurse.
            return expression;
        }

        @Override
        public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            return expression;
        }
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof AbstractFunctionInvokation) {
            ((AbstractFunctionInvokation)expression).applyExpressionRewriterToArgs(inner, ssaIdentifiers, statementContainer, flags);
        }
        return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
    }
}