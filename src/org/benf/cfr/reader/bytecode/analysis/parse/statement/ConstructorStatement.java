package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * <p/>
 * This is a temporary statement - it should be replaced with an Assignment of a ConstructorInvokation
 * However, it can force the type of the constructed object, which NEW is not capable of doing....
 */
public class ConstructorStatement extends AbstractStatement {
    private MemberFunctionInvokation invokation;

    public ConstructorStatement(MemberFunctionInvokation construction) {
        this.invokation = construction;
        Expression object = invokation.getObject();
        object.getInferredJavaType().chain(invokation.getInferredJavaType());
    }

    @Override
    public Dumper dump(Dumper dumper) {
        return dumper.print("<init>").dump(invokation).print(";\n");
    }

    @Override
    public void replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers) {
        // can't ever change, but its arguments can.
        invokation.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, getContainer());
    }

    @Override
    public void rewriteExpressions(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers) {
        invokation.applyExpressionRewriter(expressionRewriter, ssaIdentifiers, getContainer(), ExpressionRewriterFlags.RVALUE);
    }

    @Override
    public void collectObjectCreation(CreationCollector creationCollector) {
        Expression object = invokation.getObject();
        creationCollector.collectConstruction(object, invokation, this.getContainer());
    }

    @Override
    public StructuredStatement getStructuredStatement() {
        return new StructuredExpressionStatement(invokation, false);
    }
}
