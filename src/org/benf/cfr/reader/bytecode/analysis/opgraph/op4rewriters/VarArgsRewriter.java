package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.FunctionProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.GenericTypeBinder;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

public class VarArgsRewriter implements Op04Rewriter, ExpressionRewriter {

    public VarArgsRewriter() {
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        /*
         * Lambdas come in two forms - the lambda which has been produced by the java compiler,
         * which will involve an invokedynamic call, and the lambda which has been produced by
         * an anonymous inner class - this wasn't a lambda in the original code, but we should
         * consider transforming back into lambdas because we can ;)
         */

        for (StructuredStatement statement : structuredStatements) {
            statement.rewriteExpressions(this);
        }
    }

    @Override
    public void handleStatement(StatementContainer statementContainer) {
    }

    /*
     * This is the only bit of the main body which isn't boilerplate - can we lift the body?
     */
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof FunctionProcessor) {
            ((FunctionProcessor) expression).rewriteVarArgs(this);
        }
        return expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
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

    public void rewriteVarArgsArg(OverloadMethodSet overloadMethodSet, MethodPrototype methodPrototype, List<Expression> args, GenericTypeBinder gtb) {
        if (!methodPrototype.isVarArgs()) return;
        if (args.size() != methodPrototype.getArgs().size()) return;
        int last = args.size() - 1;
        Expression lastArg = args.get(args.size() - 1);
        if (!(lastArg instanceof NewAnonymousArray)) return;
        List<Expression> args2 = ListFactory.newList(args);
        args2.remove(last);
        NewAnonymousArray newAnonymousArray = (NewAnonymousArray) lastArg;

        /*
         * If newAnonymousArray is a single element 'null', we cannot split it out.
         */
        List<Expression> anonVals = newAnonymousArray.getValues();
        if (anonVals.size() == 1) {
            Literal nullLit = new Literal(TypedLiteral.getNull());
            if (anonVals.get(0).equals(nullLit)) return;
        }

        args2.addAll(newAnonymousArray.getValues());
        boolean correct = overloadMethodSet.callsCorrectEntireMethod(args2, gtb);
        if (correct) {
            args.clear();
            args.addAll(args2);
        }
        int x = 1;
    }

}
