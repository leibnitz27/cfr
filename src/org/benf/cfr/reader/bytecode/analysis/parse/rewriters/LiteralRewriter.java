package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticMonOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CastExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.NonaryFunction;

import java.util.Map;

public class LiteralRewriter extends AbstractExpressionRewriter {
    public static final LiteralRewriter INSTANCE = new LiteralRewriter(TypeConstants.OBJECT);

    // Keep track of what type we're transforming.  Note that we don't want to end up with
    // (for example) Math.Double defining that Infinity = Math.Double.Infinity ;)
    private final JavaTypeInstance testType;

    public LiteralRewriter(JavaTypeInstance testType) {
        this.testType = testType;
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
        if (!testType.equals(TypeConstants.INTEGER)) {
            if (value == Integer.MAX_VALUE) return new LValueExpression(I_MAX_VALUE);
            if (value == Integer.MIN_VALUE) return new LValueExpression(I_MIN_VALUE);
        }
        return literal;
    }

    private static final InferredJavaType INFERRED_LONG = new InferredJavaType(RawJavaType.LONG, InferredJavaType.Source.LITERAL);
    private static final StaticVariable J_MAX_VALUE = new StaticVariable(INFERRED_LONG, TypeConstants.LONG, "MAX_VALUE");
    private static final StaticVariable J_MIN_VALUE = new StaticVariable(INFERRED_LONG, TypeConstants.LONG, "MIN_VALUE");


    private Expression rewriteLong(Literal literal, long value) {
        if (!testType.equals(TypeConstants.LONG)) {
            if (value == Long.MAX_VALUE) return new LValueExpression(J_MAX_VALUE);
            if (value == Long.MIN_VALUE) return new LValueExpression(J_MIN_VALUE);
        }
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
        if (testType.equals(TypeConstants.FLOAT)) {
            if (Float.isNaN(value)) {
                return new ArithmeticOperation(Literal.FLOAT_ZERO, Literal.FLOAT_ZERO, ArithOp.DIVIDE);
            }
            if (Float.compare(Float.NEGATIVE_INFINITY, value) == 0) {
                return new ArithmeticOperation(Literal.FLOAT_MINUS_ONE, Literal.FLOAT_ZERO, ArithOp.DIVIDE);
            }
            if (Float.compare(Float.POSITIVE_INFINITY, value) == 0) {
                return new ArithmeticOperation(Literal.FLOAT_ONE, Literal.FLOAT_ZERO, ArithOp.DIVIDE);
            }
        } else {
            if (Float.isNaN(value)) return new LValueExpression(F_NAN);
            if (Float.compare(Float.NEGATIVE_INFINITY, value) == 0) return new LValueExpression(F_NEGATIVE_INFINITY);
            if (Float.compare(Float.POSITIVE_INFINITY, value) == 0) return new LValueExpression(F_POSITIVE_INFINITY);
            if (Float.compare(Float.MAX_VALUE, value) == 0) return new LValueExpression(F_MAX_VALUE);
            if (Float.compare(Float.MIN_VALUE, value) == 0) return new LValueExpression(F_MIN_VALUE);
            if (Float.compare(Float.MIN_NORMAL, value) == 0) return new LValueExpression(F_MIN_NORMAL);
        }
        // Don't have to guard, not Math.E is double.
        if (Float.compare((float) Math.E, value) == 0) return new CastExpression(INFERRED_FLOAT, new LValueExpression(MATH_E));
        Expression piExpr = maybeGetPiExpression(value);
        if (piExpr != null) return piExpr;
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
        if (testType.equals(TypeConstants.DOUBLE)) {
            if (Double.isNaN(value)) {
                return new ArithmeticOperation(Literal.DOUBLE_ZERO, Literal.DOUBLE_ZERO, ArithOp.DIVIDE);
            }
            if (Double.compare(Double.NEGATIVE_INFINITY, value) == 0) {
                return new ArithmeticOperation(Literal.DOUBLE_MINUS_ONE, Literal.DOUBLE_ZERO, ArithOp.DIVIDE);
            }
            if (Double.compare(Double.POSITIVE_INFINITY, value) == 0) {
                return new ArithmeticOperation(Literal.DOUBLE_ONE, Literal.DOUBLE_ZERO, ArithOp.DIVIDE);
            }
        } else {
            if (Double.isNaN(value)) return new LValueExpression(D_NAN);
            if (Double.compare(Double.NEGATIVE_INFINITY, value) == 0) return new LValueExpression(D_NEGATIVE_INFINITY);
            if (Double.compare(Double.POSITIVE_INFINITY, value) == 0) return new LValueExpression(D_POSITIVE_INFINITY);
            if (Double.compare(Double.MAX_VALUE, value) == 0) return new LValueExpression(D_MAX_VALUE);
            if (Double.compare(Double.MIN_VALUE, value) == 0) return new LValueExpression(D_MIN_VALUE);
            if (Double.compare(Double.MIN_NORMAL, value) == 0) return new LValueExpression(D_MIN_NORMAL);
        }
        if (!testType.equals(TypeConstants.MATH)) {
            if (Double.compare(Math.E, value) == 0) return new LValueExpression(MATH_E);
            float nearestFloat = (float) value;
            if (Double.compare(nearestFloat, value) == 0) {
                // "(double)".length() == 8, "f" suffix is one more
                if (Float.toString(nearestFloat).length() + 9 < Double.toString(value).length()) {
                    return new CastExpression(INFERRED_DOUBLE, new Literal(TypedLiteral.getFloat(nearestFloat)));
                }
            }
            Expression piExpr = maybeGetPiExpression(value);
            if (piExpr != null) return piExpr;
        }
        return literal;
    }

    // NB : Normal 'set of ieee754' complaint doesn't occur here, as these are comparing against
    // compile time constants (and we're calculating them with strictfp to match).
    private static final Map<Double, NonaryFunction<Expression>> PI_DOUBLES = MapFactory.newMap();
    private static final Map<Float, NonaryFunction<Expression>> PI_FLOATS = MapFactory.newMap();

    static {
        final Expression pi = new LValueExpression(MATH_PI);
        final Expression npi = new ArithmeticMonOperation(pi, ArithOp.MINUS);
        for (int i = -10; i <= 10; i++) {
            if (i == 0) continue;

            final int ii = i;
            NonaryFunction<Expression> pifn = new NonaryFunction<Expression>() {
                @Override
                public Expression invoke() {
                    switch (ii) {
                        case 1:
                            return pi;
                        case -1:
                            return npi;
                        default:
                            return new ArithmeticOperation(pi, new Literal(TypedLiteral.getInt(ii)), ArithOp.MULTIPLY);
                    }
                }
            };
            PI_DOUBLES.put(Math.PI * i, pifn);

            pifn = new NonaryFunction<Expression>() {
                @Override
                public Expression invoke() {
                    switch (ii) {
                        case 1:
                            return new CastExpression(INFERRED_FLOAT, pi);
                        case -1:
                            return new CastExpression(INFERRED_FLOAT, npi);
                        default:
                            return new CastExpression(INFERRED_FLOAT, new ArithmeticOperation(pi, new Literal(TypedLiteral.getInt(ii)), ArithOp.MULTIPLY));
                    }
                }
            };
            PI_FLOATS.put((float)(Math.PI * i), pifn);

            if (Math.abs(i) < 2) continue;

            pifn = new NonaryFunction<Expression>() {
                @Override
                public Expression invoke() {
                    return new ArithmeticOperation(new CastExpression(INFERRED_FLOAT, pi), new Literal(TypedLiteral.getInt(ii)), ArithOp.MULTIPLY);
                }
            };
            PI_FLOATS.put((float)(Math.PI) * i, pifn);
        }
        for (int i = -4; i <= 4; i++) {
            if (i == 0) continue;
            final int ii = i;
            final Expression p = i < 0 ? npi : pi;
            NonaryFunction<Expression> pifn = new NonaryFunction<Expression>() {
                @Override
                public Expression invoke() {
                    return new ArithmeticOperation(p, new Literal(TypedLiteral.getInt(90 * Math.abs(ii))), ArithOp.DIVIDE);
                }
            };
            PI_DOUBLES.put(Math.PI / (90 * i), pifn);

            pifn = new NonaryFunction<Expression>() {
                @Override
                public Expression invoke() {
                    return new CastExpression(INFERRED_FLOAT, new ArithmeticOperation(p, new Literal(TypedLiteral.getInt(90 * Math.abs(ii))), ArithOp.DIVIDE));
                }
            };
            PI_FLOATS.put((float)(Math.PI / (90 * i)), pifn);

            pifn = new NonaryFunction<Expression>() {
                @Override
                public Expression invoke() {
                    return new ArithmeticOperation(new CastExpression(INFERRED_FLOAT, p), new Literal(TypedLiteral.getInt(90 * Math.abs(ii))), ArithOp.DIVIDE);
                }
            };
            PI_FLOATS.put((float)(Math.PI) / (90 * i), pifn);
        }
    }

    private static Expression maybeGetPiExpression(float value) {
        NonaryFunction<Expression> e = PI_FLOATS.get(value);
        if (null == e) return null;
        return e.invoke();
    }

    private static Expression maybeGetPiExpression(double value) {
        NonaryFunction<Expression> e = PI_DOUBLES.get(value);
        if (null == e) return null;
        return e.invoke();
    }
}
