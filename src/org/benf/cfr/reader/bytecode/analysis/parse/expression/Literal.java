package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.SentinelLocalClassLValue;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionVisitor;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.EquivalenceConstraint;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.ReadWrite;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Map;

public class Literal extends AbstractExpression {
    public static final Literal FALSE = new Literal(TypedLiteral.getBoolean(0));
    public static final Literal TRUE = new Literal(TypedLiteral.getBoolean(1));
    public static final Literal MINUS_ONE = new Literal(TypedLiteral.getInt(-1));
    public static final Literal NULL = new Literal(TypedLiteral.getNull());
    // Avoid using directly, as you'll probably end up accidentally implementing equalsAnyOne
    // (I.e. you want to be using equalsAnyOne).
    public static final Literal INT_ZERO = new Literal(TypedLiteral.getInt(0));
    public static final Literal INT_ONE = new Literal(TypedLiteral.getInt(1));
    private static final Literal LONG_ONE = new Literal(TypedLiteral.getLong(1));

    public static final Literal DOUBLE_ZERO = new Literal(TypedLiteral.getDouble(0.0));
    public static final Literal DOUBLE_ONE = new Literal(TypedLiteral.getDouble(1.0));
    public static final Literal DOUBLE_MINUS_ONE = new Literal(TypedLiteral.getDouble(-1.0));

    public static final Literal FLOAT_ZERO = new Literal(TypedLiteral.getFloat(0.0f));
    public static final Literal FLOAT_ONE = new Literal(TypedLiteral.getFloat(1.0f));
    public static final Literal FLOAT_MINUS_ONE = new Literal(TypedLiteral.getFloat(-1.0f));

    protected final TypedLiteral value;

    public Literal(TypedLiteral value) {
        super(BytecodeLoc.NONE, value.getInferredJavaType());
        this.value = value;
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        // Not strictly right, but we don't want every 'true' to be distinct!!
        return BytecodeLoc.NONE;
    }

    public static Expression getLiteralOrNull(RawJavaType rawCastType, InferredJavaType inferredCastType, int intValue) {
        switch (rawCastType) {
            case BOOLEAN:
                if (intValue == 0) return FALSE;
                if (intValue == 1) return TRUE;
                return null;
            case BYTE:
            case CHAR:
            case SHORT:
                return new CastExpression(BytecodeLoc.NONE, inferredCastType, new Literal(TypedLiteral.getInt(intValue)));
            case INT:
                return new Literal(TypedLiteral.getInt(intValue));
            case LONG:
                return new Literal(TypedLiteral.getLong(intValue));
            case FLOAT:
                return new Literal(TypedLiteral.getFloat(intValue));
            case DOUBLE:
                return new Literal(TypedLiteral.getDouble(intValue));
            default:
                return null;
        }
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.HIGHEST;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        return d.dump(value);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        value.collectTypeUsages(collector);
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return this;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }

    @Override
    public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return this;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        if (value.getType() == TypedLiteral.LiteralType.Class) {
            Object x = value.getValue();
            if (x instanceof JavaTypeInstance) {
                JavaTypeInstance lValueType = (JavaTypeInstance)x;
                InnerClassInfo innerClassInfo = lValueType.getInnerClassHereInfo();

                if (innerClassInfo.isMethodScopedClass() && !innerClassInfo.isAnonymousClass()) {
                    lValueUsageCollector.collect(new SentinelLocalClassLValue(lValueType), ReadWrite.READ);
                }
            }
        }
    }

    public Expression appropriatelyCasted(InferredJavaType expected) {
        if (value.getType() != TypedLiteral.LiteralType.Integer) return this;
        JavaTypeInstance type = expected.getJavaTypeInstance();
        if (type.getStackType() != StackType.INT) return this;
        if (type == RawJavaType.SHORT ||
            type == RawJavaType.BYTE ||
            type == RawJavaType.CHAR) return new CastExpression(BytecodeLoc.NONE, expected, this);
        return this;
    }

    public TypedLiteral getValue() {
        return value;
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Literal)) return false;
        Literal other = (Literal) o;
        return value.equals(other.value);
    }

    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof Literal)) return false;
        Literal other = (Literal) o;
        if (!constraint.equivalent(value, other.value)) return false;
        return true;
    }

    @Override
    public Literal getComputedLiteral(Map<LValue, Literal> display) {
        return this;
    }

    public static boolean equalsAnyOne(Expression expression) {
        return expression.equals(Literal.INT_ONE) || expression.equals(Literal.LONG_ONE);
    }
}
