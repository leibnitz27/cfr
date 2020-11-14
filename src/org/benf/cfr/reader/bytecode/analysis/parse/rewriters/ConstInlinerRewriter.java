package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractConstructorInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractNewArray;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArrayIndex;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ComparisonOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NewAnonymousArray;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NewObjectArray;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NewPrimitiveArray;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.ArrayVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType.Source;
import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.ArrayList;
import java.util.List;

public class ConstInlinerRewriter extends AbstractExpressionRewriter {
	public static final ConstInlinerRewriter INSTANCE = new ConstInlinerRewriter();

	@Override
	public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
		expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
		if (expression instanceof ArithmeticOperation) {
			ArithmeticOperation operation = (ArithmeticOperation) expression;
			Expression computed = operation.getComputedLiteral(MapFactory.<LValue, Literal>newMap());
			if (computed != null) {
				expression = computed;
			}
		}
		return expression;
	}

	@Override
	public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
		lValue.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
		return lValue;
	}
}
