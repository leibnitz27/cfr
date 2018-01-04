package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NewAnonymousArray;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.FunctionProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.types.GenericTypeBinder;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

// It's not normally valid to use an implicit narrowing conversion, however in the case where this is an assignment,
// it is.
public class NarrowingAssignmentRewriter implements Op04Rewriter {

    public NarrowingAssignmentRewriter() {
    }

    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> statements = MiscStatementTools.linearise(root);
        if (statements == null) return;
        for (StructuredStatement s : statements) {
            if (!(s instanceof StructuredAssignment)) continue;
            StructuredAssignment ass = (StructuredAssignment)s;
            LValue lValue = ass.getLvalue();
            RawJavaType raw =  RawJavaType.getUnboxedTypeFor(lValue.getInferredJavaType().getJavaTypeInstance());
            if (raw == null) continue;
            Expression rhs = ass.getRvalue();
            if (!(rhs instanceof CastExpression)) continue;
            CastExpression exp = (CastExpression)rhs;
            if (!(exp.isForced() && exp.getInferredJavaType().getRawType() == raw)) continue;
            s.rewriteExpressions(new ExpressionReplacingRewriter(exp, exp.getChild()));
        }
    }
}
