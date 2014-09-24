package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.List;

public class StringBuilderRewriter implements ExpressionRewriter {
    private final boolean stringBuilderEnabled;
    private final boolean stringBufferEnabled;

    public StringBuilderRewriter(Options options, ClassFileVersion classFileVersion) {
        this.stringBufferEnabled = options.getOption(OptionsImpl.SUGAR_STRINGBUFFER, classFileVersion);
        this.stringBuilderEnabled = options.getOption(OptionsImpl.SUGAR_STRINGBUILDER, classFileVersion);
    }

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

//    @Override
//    public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
//        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
//        return (AbstractAssignmentExpression) res;
//    }

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
                    Expression e = memberFunctionInvokation.getAppropriatelyCastArgument(0);
                    if (e instanceof CastExpression) {
                        Expression ce = ((CastExpression) e).getChild();
                        if (ce.getInferredJavaType().getJavaTypeInstance().implicitlyCastsTo(e.getInferredJavaType().getJavaTypeInstance(), null)) {
                            e = ce;
                        }
                    }
                    reverseAppendChain.add(e);
                } else {
                    return null;
                }
            } else if (lhs instanceof ConstructorInvokationSimple) {
                ConstructorInvokationSimple newObject = (ConstructorInvokationSimple) lhs;
                String rawName = newObject.getTypeInstance().getRawName();
                if ((stringBuilderEnabled && rawName.equals(TypeConstants.stringBuilderName)) ||
                        (stringBufferEnabled && rawName.equals(TypeConstants.stringBufferName))) {
                    // If the constructor has an argument of a String or a CharSequence, we need to add
                    // that to the reverseAppendChain too!
                    switch (newObject.getArgs().size()) {
                        default:
                            return null;
                        case 1: {
                            Expression e = newObject.getArgs().get(0);
                            String typeName = e.getInferredJavaType().getJavaTypeInstance().getRawName();
                            if (typeName.equals(TypeConstants.stringName)) {
                                // Could also do it for type sequences, but .....
                                if (e instanceof CastExpression) {
                                    Expression ce = ((CastExpression) e).getChild();
                                    if (ce.getInferredJavaType().getJavaTypeInstance().implicitlyCastsTo(e.getInferredJavaType().getJavaTypeInstance(), null)) {
                                        e = ce;
                                    }
                                }
                                reverseAppendChain.add(e);
                            } else {
                                return null;
                            }
                        }
                        // fall through.
                        case 0:
                            return genStringConcat(reverseAppendChain);
                    }
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

        JavaTypeInstance lastType = revList.get(revList.size() - 1).getInferredJavaType().getJavaTypeInstance();
        if (lastType instanceof RawJavaType) {
            revList.add(new Literal(TypedLiteral.getString("\"\"")));
        }

        int x = revList.size() - 1;
        if (x < 0) return null;
        Expression head = revList.get(x);

//        ClassFile stringClass = cfrState.getClassFile(TypeConstants.stringName, false);
//        if (stringClass == null) return null;
//        JavaTypeInstance stringType = stringClass.getClassType();
        InferredJavaType inferredJavaType = new InferredJavaType(TypeConstants.STRING, InferredJavaType.Source.STRING_TRANSFORM, true);
        for (--x; x >= 0; --x) {
            Expression appendee = revList.get(x);
//            if (appendee instanceof ArithmeticOperation && appendee.getPrecedence().compareTo(Precedence.ADD_SUB) <= 0) {
//                appendee = new ExplicitBraceExpression(appendee);
//            }
            head = new ArithmeticOperation(inferredJavaType, head, appendee, ArithOp.PLUS);
        }
        return head;
    }
}
