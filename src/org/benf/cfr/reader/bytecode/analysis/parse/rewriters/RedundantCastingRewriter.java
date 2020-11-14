package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.util.collections.MapFactory;

public class RedundantCastingRewriter extends AbstractExpressionRewriter {
	public static final RedundantCastingRewriter INSTANCE = new RedundantCastingRewriter();

	@Override
	public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
		expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
		// Simplify casts for cases like "(int) 0" into just "0"
		if (expression instanceof CastExpression) {
			Expression computed = expression.getComputedLiteral(MapFactory.<LValue, Literal>newMap());
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
