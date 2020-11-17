package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticMonOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.Map;

public class ConstantFoldingRewriter extends AbstractExpressionRewriter {
	public static final ConstantFoldingRewriter INSTANCE = new ConstantFoldingRewriter();
	private static final Map<LValue, Literal> DISPLAY_MAP = MapFactory.<LValue, Literal>newMap();

	@Override
	public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
		expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
		// Skip if expression type is non-primitive
		JavaTypeInstance type = expression.getInferredJavaType().getJavaTypeInstance();
		if (type instanceof RawJavaType) {
			RawJavaType rawType = (RawJavaType) type;
			if (rawType.ordinal() > RawJavaType.DOUBLE.ordinal())
				return expression;
		}
		// Simplify arithmetic / casting by replacing with the computed value
		Expression computed = expression.getComputedLiteral(getDisplayMap());
		if (computed != null) {
			expression = computed;
		}
		return expression;
	}

	@Override
	public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
		lValue.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
		return lValue;
	}

	private Map<LValue, Literal> getDisplayMap() {
		// TODO: It would be cool to later populate this map so variables that behave
		//  as constants can be folded as well. This would be more simple in Op03 stage.
		return DISPLAY_MAP;
	}
}
