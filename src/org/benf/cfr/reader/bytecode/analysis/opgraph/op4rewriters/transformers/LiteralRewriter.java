package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.Optional;

import java.util.HashSet;
import java.util.Set;

// TODO: handle the actual definitions of the constants differently
public class LiteralRewriter extends AbstractExpressionRewriter implements StructuredStatementTransformer {
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

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof Literal) {
            Literal literal = (Literal) expression;
            TypedLiteral typed = literal.getValue();
            TypedLiteral.LiteralType type = typed.getType();
            switch (type) {
                case Integer: return rewriteInteger(literal, typed.getIntValue());
                case Long: return rewriteLong(literal, typed.getLongValue());
                case Double: return rewriteDouble(literal, typed.getDoubleValue());
                case Float: return rewriteFloat(literal, typed.getFloatValue());
            }
        }
        return expression;
    }

    private static final InferredJavaType INFERRED_INT = new InferredJavaType(RawJavaType.INT, InferredJavaType.Source.LITERAL);
    private static final StaticVariable I_MAX_VALUE = new StaticVariable(INFERRED_INT, TypeConstants.INTEGER, "MAX_VALUE");
    private static final StaticVariable I_MIN_VALUE = new StaticVariable(INFERRED_INT, TypeConstants.INTEGER, "MIN_VALUE");

    private Expression rewriteInteger(Literal literal, int value) {
        if (value == Integer.MAX_VALUE) return new LValueExpression(I_MAX_VALUE);
        if (value == Integer.MIN_VALUE) return new LValueExpression(I_MIN_VALUE);
        return literal;
    }

    private static final InferredJavaType INFERRED_LONG = new InferredJavaType(RawJavaType.LONG, InferredJavaType.Source.LITERAL);
    private static final StaticVariable J_MAX_VALUE = new StaticVariable(INFERRED_LONG, TypeConstants.LONG, "MAX_VALUE");
    private static final StaticVariable J_MIN_VALUE = new StaticVariable(INFERRED_LONG, TypeConstants.LONG, "MIN_VALUE");


    private Expression rewriteLong(Literal literal, long value) {
        if (value == Long.MAX_VALUE) return new LValueExpression(J_MAX_VALUE);
        if (value == Long.MIN_VALUE) return new LValueExpression(J_MIN_VALUE);
        if (value == Integer.MAX_VALUE) return new LValueExpression(I_MAX_VALUE);
        if (value == Integer.MIN_VALUE) return new LValueExpression(I_MIN_VALUE);
        return literal;
    }

    private static final InferredJavaType INFERRED_FLOAT = new InferredJavaType(RawJavaType.FLOAT, InferredJavaType.Source.LITERAL);
    private static final StaticVariable F_MAX_VALUE = new StaticVariable(INFERRED_FLOAT, TypeConstants.FLOAT, "MAX_VALUE");
    private static final StaticVariable F_MIN_VALUE = new StaticVariable(INFERRED_FLOAT, TypeConstants.FLOAT, "MIN_VALUE");
    private static final StaticVariable F_MIN_NORMAL = new StaticVariable(INFERRED_FLOAT, TypeConstants.FLOAT, "MIN_NORMAL");
    private static final StaticVariable F_NAN = new StaticVariable(INFERRED_FLOAT, TypeConstants.FLOAT, "NaN");
    private static final StaticVariable F_NEGATIVE_INFINITY = new StaticVariable(INFERRED_FLOAT, TypeConstants.FLOAT, "NEGATIVE_INFINITY");
    private static final StaticVariable F_POSITIVE_INFINITY = new StaticVariable(INFERRED_FLOAT, TypeConstants.FLOAT, "POSITIVE_INFINITY");

    private Expression rewriteFloat(Literal literal, float value) {
        if (Float.isNaN(value)) return new LValueExpression(F_NAN);
        if (Float.compare(Float.NEGATIVE_INFINITY, value) == 0) return new LValueExpression(F_NEGATIVE_INFINITY);
        if (Float.compare(Float.POSITIVE_INFINITY, value) == 0) return new LValueExpression(F_POSITIVE_INFINITY);
        if (Float.compare(Float.MAX_VALUE, value) == 0) return new LValueExpression(F_MAX_VALUE);
        if (Float.compare(Float.MIN_VALUE, value) == 0) return new LValueExpression(F_MIN_VALUE);
        if (Float.compare(Float.MIN_NORMAL, value) == 0) return new LValueExpression(F_MIN_NORMAL);
        if (Float.compare((float) Math.E, value) == 0) return new CastExpression(INFERRED_FLOAT, new LValueExpression(MATH_E));
        Optional<Expression> piExpr = maybeGetPiExpression(value);
        if (piExpr.isSet()) return piExpr.getValue();
        return literal;
    }

    private static final InferredJavaType INFERRED_DOUBLE = new InferredJavaType(RawJavaType.DOUBLE, InferredJavaType.Source.LITERAL);
    private static final StaticVariable D_MAX_VALUE = new StaticVariable(INFERRED_FLOAT, TypeConstants.DOUBLE, "MAX_VALUE");
    private static final StaticVariable D_MIN_VALUE = new StaticVariable(INFERRED_FLOAT, TypeConstants.DOUBLE, "MIN_VALUE");
    private static final StaticVariable D_MIN_NORMAL = new StaticVariable(INFERRED_FLOAT, TypeConstants.DOUBLE, "MIN_NORMAL");
    private static final StaticVariable D_NAN = new StaticVariable(INFERRED_DOUBLE, TypeConstants.DOUBLE, "NaN");
    private static final StaticVariable D_NEGATIVE_INFINITY = new StaticVariable(INFERRED_DOUBLE, TypeConstants.DOUBLE, "NEGATIVE_INFINITY");
    private static final StaticVariable D_POSITIVE_INFINITY = new StaticVariable(INFERRED_DOUBLE, TypeConstants.DOUBLE, "POSITIVE_INFINITY");
    private static final StaticVariable MATH_PI = new StaticVariable(INFERRED_DOUBLE, TypeConstants.MATH, "PI");
    private static final StaticVariable MATH_E = new StaticVariable(INFERRED_DOUBLE, TypeConstants.MATH, "E");

    private Expression rewriteDouble(Literal literal, double value) {
        if (Double.isNaN(value)) return new LValueExpression(D_NAN);
        if (Double.compare(Double.NEGATIVE_INFINITY, value) == 0) return new LValueExpression(D_NEGATIVE_INFINITY);
        if (Double.compare(Double.POSITIVE_INFINITY, value) == 0) return new LValueExpression(D_POSITIVE_INFINITY);
        if (Double.compare(Double.MAX_VALUE, value) == 0) return new LValueExpression(D_MAX_VALUE);
        if (Double.compare(Double.MIN_VALUE, value) == 0) return new LValueExpression(D_MIN_VALUE);
        if (Double.compare(Double.MIN_NORMAL, value) == 0) return new LValueExpression(D_MIN_NORMAL);
        if (Double.compare(Math.E, value) == 0) return new LValueExpression(MATH_E);
        float nearestFloat = (float) value;
        if (Double.compare(nearestFloat, value) == 0) {
            // "(double)".length() == 8, "f" suffix is one more
            if (Float.toString(nearestFloat).length() + 9 < Double.toString(value).length()) {
                return new CastExpression(INFERRED_DOUBLE, new Literal(TypedLiteral.getFloat(nearestFloat)));
            }
        }
        Optional<Expression> piExpr = maybeGetPiExpression(value);
        if (piExpr.isSet()) return piExpr.getValue();
        return literal;
    }

    private static final Set<Double> PI_DOUBLES = new HashSet<Double>();
    private static final Set<Float> PI_FLOATS = new HashSet<Float>();

    static {
        for (int i = -10; i <= 10; i++) {
            if (i == 0) continue;
            PI_DOUBLES.add(Math.PI * i);
            PI_FLOATS.add((float)(Math.PI * i));
        }
        for (int i = -4; i <= 4; i++) {
            if (i >= -1 && i <= 1) continue;
            PI_DOUBLES.add(Math.PI / (90 * i));
            PI_FLOATS.add((float)(Math.PI / (90 * i)));
        }
    }

    private static Optional<Expression> maybeGetPiExpression(float value) {
        if (!PI_FLOATS.contains(value)) return Optional.empty();
        return Optional.<Expression>of(new CastExpression(INFERRED_FLOAT, getPiExpression(value)));
    }

    private static Optional<Expression> maybeGetPiExpression(double value) {
        if (!PI_DOUBLES.contains(value)) return Optional.empty();
        return Optional.of(getPiExpression(value));
    }

    private static Expression getPiExpression(double value) {
        if (Math.abs(value) < Math.PI) {
            int divisor = (int) Math.round(Math.PI / value);
            if (divisor < 0) {
                // -Math.PI / n
                return new ArithmeticOperation(new ArithmeticMonOperation(new LValueExpression(MATH_PI), ArithOp.MINUS), new Literal(TypedLiteral.getInt(-divisor)), ArithOp.DIVIDE);
            }
            // Math.PI / n
            return new ArithmeticOperation(new LValueExpression(MATH_PI), new Literal(TypedLiteral.getInt(divisor)), ArithOp.DIVIDE);
        } else {
            int factor = (int) Math.round(value / Math.PI);
            if (factor == 1) return new LValueExpression(MATH_PI);
            if (factor == -1) return new ArithmeticMonOperation(new LValueExpression(MATH_PI), ArithOp.MINUS);
            // Math.PI * n
            return new ArithmeticOperation(new LValueExpression(MATH_PI), new Literal(TypedLiteral.getInt(factor)), ArithOp.MULTIPLY);
        }
    }
}
