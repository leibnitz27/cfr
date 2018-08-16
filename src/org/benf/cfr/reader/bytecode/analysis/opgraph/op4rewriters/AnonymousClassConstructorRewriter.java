package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationAnonymousInner;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.SuperFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;

import java.util.List;

public class AnonymousClassConstructorRewriter extends AbstractExpressionRewriter {
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression = super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof ConstructorInvokationAnonymousInner) {
            ClassFile classFile = ((ConstructorInvokationAnonymousInner) expression).getClassFile();
            for (Method constructor : classFile.getConstructors()) {
                Op04StructuredStatement analysis = constructor.getAnalysis();
                /*
                 * nop out initial super call, if present.
                 */
                if (!(analysis.getStatement() instanceof Block)) continue;
                Block block = (Block)analysis.getStatement();
                List<Op04StructuredStatement> statements = block.getBlockStatements();
                for (Op04StructuredStatement stmCont : statements) {
                    StructuredStatement stm = stmCont.getStatement();
                    if (stm instanceof StructuredComment) continue;
                    if (stm instanceof StructuredExpressionStatement) {
                        Expression e = ((StructuredExpressionStatement) stm).getExpression();
                        if (e instanceof SuperFunctionInvokation) {
                            stmCont.nopOut();
                            break;
                        }
                    }
                    break;
                }
            }
        }
        return expression;
    }
}
