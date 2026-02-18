package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;

import java.util.List;

public class StringIndexOfTidier extends AbstractExpressionRewriter implements StructuredStatementTransformer {

    public void transform(Op04StructuredStatement root) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        in.transformStructuredChildren(this, scope);
        in.rewriteExpressions(this);
        return in;
    }

    // Searching for specific pattern - string indexOf(97) -> indexOF('a').
    private static AbstractMemberFunctionInvokation applyTransforms(AbstractMemberFunctionInvokation f) {
        List<Expression> args = f.getArgs();
        if (args.isEmpty()) return f;

        String name = f.getMethodPrototype().getName();
        if (!(name.equals("indexOf") || name.equals("lastIndexOf"))) return f;

        Expression arg = args.get(0);
        if (!(arg instanceof Literal)) return f;

        JavaTypeInstance classType = f.getFunction().getClassEntry().getTypeInstance();
        if (!TypeConstants.STRING.equals(classType.getDeGenerifiedType())) return f;
        Literal literal = (Literal) arg;
        TypedLiteral typed = literal.getValue();
        if (typed.getType() != TypedLiteral.LiteralType.Integer) return f;
        if (typed.getInferredJavaType().getRawType() != RawJavaType.INT) return f;
        int value = typed.getIntValue();
        if (value < Character.MIN_VALUE || value > Character.MAX_VALUE) return f;
        args.set(0, new Literal(TypedLiteral.getChar(value)));
        return f;
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof AbstractMemberFunctionInvokation) {
            expression = applyTransforms((AbstractMemberFunctionInvokation)expression);
        }
        return expression;
    }
}
