package org.benf.cfr.reader.bytecode.analysis.parse.literal;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;

public class LiteralFolding {
    /*
     * Fold an arith op.
     * Anything that may fail at runtime must return null.
     */
    public static Literal foldArith(RawJavaType returnType, Literal l, Literal r, ArithOp op) {
        if (!returnType.isNumber()) return null;
        l = foldCast(l, returnType);
        if (l == null) return null;
        // In the case of SHR/SHL/SHRU, this isn't actually true, but for all values
        // that can be used, it isn't *WRONG*.
        r = foldCast(r, returnType);
        if (r == null) return null;
        // We assume that all operations occur at type promotion to return type.
        TypedLiteral tl = getArith(returnType, l.getValue(), r.getValue(), op);
        if (tl == null) return null;
        return new Literal(tl);
    }

    private static TypedLiteral getArith(RawJavaType type, TypedLiteral l, TypedLiteral r, ArithOp op) {
        switch (type) {
            case BYTE: {
                Integer val = getArith(l.getIntValue(), r.getIntValue(), op);
                if (val == null) return null;
                return TypedLiteral.getInt((byte)(int)val, type);
            }
            case SHORT: {
                Integer val = getArith(l.getIntValue(), r.getIntValue(), op);
                if (val == null) return null;
                return TypedLiteral.getInt((short)(int)val, type);
            }
            case INT: {
                Integer val = getArith(l.getIntValue(), r.getIntValue(), op);
                if (val == null) return null;
                return TypedLiteral.getInt(val, type);
            }
            case LONG: {
                Long val = getArith(l.getLongValue(), r.getLongValue(), op);
                if (val == null) return null;
                return TypedLiteral.getLong(val);
            }
            case FLOAT: {
                Float val = getArith(l.getFloatValue(), r.getFloatValue(), op);
                if (val == null) return null;
                return TypedLiteral.getFloat(val);
            }
            case DOUBLE: {
                Double val = getArith(l.getDoubleValue(), r.getDoubleValue(), op);
                if (val == null) return null;
                return TypedLiteral.getDouble(val);
            }
        }
        return null;
    }

    private static Double getArith(double l, double r, ArithOp op) {
        switch (op) {
            case PLUS:
                return l+r;
            case MINUS:
                return l-r;
            case MULTIPLY:
                return l*r;
            case DIVIDE:
                return l/r;
            case REM:
                return l%r;
        }
        return null;
    }

    private static Float getArith(float l, float r, ArithOp op) {
        switch (op) {
            case PLUS:
                return l+r;
            case MINUS:
                return l-r;
            case MULTIPLY:
                return l*r;
            case DIVIDE:
                return l/r;
            case REM:
                return l%r;
        }
        return null;
    }

    private static Long getArith(long l, long r, ArithOp op) {
        switch (op) {
            case PLUS:
                return l + r;
            case MINUS:
                return l - r;
            case MULTIPLY:
                return l * r;
            case DIVIDE:
                if (r == 0) return null;
                return l / r;
            case REM:
                if (r == 0) return null;
                return l % r;
            case SHR:
                return l >> r;
            case SHL:
                return l << r;
            case SHRU:
                return l >>> r;
            case XOR:
                return l ^ r;
        }
        return null;
    }

    private static Integer getArith(int l, int r, ArithOp op) {
        switch (op) {
            case PLUS:
                return l + r;
            case MINUS:
                return l - r;
            case MULTIPLY:
                return l * r;
            case DIVIDE:
                if (r == 0) return null;
                return l / r;
            case REM:
                if (r == 0) return null;
                return l % r;
            case OR:
                return l | r;
            case AND:
                return l & r;
            case SHR:
                return l >> r;
            case SHL:
                return l << r;
            case SHRU:
                return l >>> r;
            case XOR:
                return l ^ r;
        }
        return null;
    }

    public static Literal foldCast(Literal val, RawJavaType returnType) {
        RawJavaType fromType = getRawType(val);
        if (fromType == null) return null;
        if (!fromType.isNumber()) return null;
        if (!returnType.isNumber()) return null;
        TypedLiteral tl = getCast(val.getValue(), fromType, returnType);
        if (tl == null) return null;
        return new Literal(tl);
    }

    /*
     * Yes, this is incredibly tedious.  We could probably do some reflection based technique, but it's certainly
     * NOT reasonable to convert into a common type to remove the square of cases.
     */
    private static TypedLiteral getCast(TypedLiteral val, RawJavaType fromType, RawJavaType returnType) {
        if (fromType == returnType) return val;
        switch (returnType) {
            case BYTE:
                switch (fromType) {
                    case SHORT:
                    case INT:
                        return TypedLiteral.getInt((byte)val.getIntValue(), returnType);
                    case LONG:
                        return TypedLiteral.getInt((byte)val.getLongValue());
                    case FLOAT:
                        return TypedLiteral.getInt((byte)val.getFloatValue());
                    case DOUBLE:
                        return TypedLiteral.getInt((byte)val.getDoubleValue());
                }
                break;
            case SHORT:
                switch (fromType) {
                    case BYTE:
                    case INT:
                        return TypedLiteral.getInt((short)val.getIntValue(), returnType);
                    case LONG:
                        return TypedLiteral.getInt((short)val.getLongValue());
                    case FLOAT:
                        return TypedLiteral.getInt((short)val.getFloatValue());
                    case DOUBLE:
                        return TypedLiteral.getInt((short)val.getDoubleValue());
                }
                break;
            case INT:
                switch (fromType) {
                    case BYTE:
                    case SHORT:
                        return TypedLiteral.getInt(val.getIntValue(), returnType);
                    case LONG:
                        return TypedLiteral.getInt((int)val.getLongValue());
                    case FLOAT:
                        return TypedLiteral.getInt((int)val.getFloatValue());
                    case DOUBLE:
                        return TypedLiteral.getInt((int)val.getDoubleValue());
                }
                break;
            case LONG:
                switch (fromType) {
                    case BYTE:
                    case SHORT:
                    case INT:
                        return TypedLiteral.getLong(val.getIntValue());
                    case FLOAT:
                        return TypedLiteral.getLong((long)val.getFloatValue());
                    case DOUBLE:
                        return TypedLiteral.getLong((long)val.getDoubleValue());
                }
                break;
            case FLOAT:
                switch (fromType) {
                    case BYTE:
                    case SHORT:
                    case INT:
                        return TypedLiteral.getFloat(val.getIntValue());
                    case LONG:
                        return TypedLiteral.getFloat((float)val.getLongValue());
                    case DOUBLE:
                        return TypedLiteral.getFloat((float)val.getDoubleValue());
                }
                break;
            case DOUBLE:
                switch (fromType) {
                    case BYTE:
                    case SHORT:
                    case INT:
                        return TypedLiteral.getDouble(val.getIntValue());
                    case LONG:
                        return TypedLiteral.getDouble(val.getLongValue());
                    case FLOAT:
                        return TypedLiteral.getDouble(val.getFloatValue());
                }
                break;
        }
        return null;
    }

    // Returns null if not raw type.
    private static RawJavaType getRawType(Literal l) {
        JavaTypeInstance typ = l.getInferredJavaType().getJavaTypeInstance();
        if (typ instanceof RawJavaType) return (RawJavaType)typ;
        return null;
    }
}
