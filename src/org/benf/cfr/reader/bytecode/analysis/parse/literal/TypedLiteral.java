package org.benf.cfr.reader.bytecode.analysis.parse.literal;

import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 28/03/2012
 * Time: 05:42
 * To change this template use File | Settings | File Templates.
 */
public class TypedLiteral {

    public enum LiteralType {
        Integer,
        Long,
        Double,
        String,
        NullObject,
        Class;
    }

    private final InferredJavaType inferredJavaType;
    private final LiteralType type;
    private final Object value;

    protected TypedLiteral(LiteralType type, InferredJavaType inferredJavaType, Object value) {
        this.type = type;
        this.value = value;
        this.inferredJavaType = inferredJavaType;
    }

    private static String integerName(Object o) {
        if (!(o instanceof Integer)) return o.toString();
        int i = (Integer) o;
        switch (i) {
            case Integer.MAX_VALUE:
                return "Integer.MAX_VALUE";
            case Integer.MIN_VALUE:
                return "Integer.MIN_VALUE";
            default:
                return o.toString();
        }
    }

    private static String charName(Object o) {
        if (!(o instanceof Integer)) throw new ConfusedCFRException("Expecting char-as-int");
        int i = (Integer) o;
        char c = (char) i;
        switch (c) {
            case '\r':
                return "'\\r'";
            case '\n':
                return "'\\n'";
            case '\t':
                return "'\\t'";
            default:
                return "'" + c + "'";
        }
    }

    private static String boolName(Object o) {
        if (!(o instanceof Integer)) throw new ConfusedCFRException("Expecting boolean-as-int");
        int i = (Integer) o;
        switch (i) {
            case 0:
                return "false";
            case 1:
                return "true";
            default:
                throw new ConfusedCFRException("Expecting a boolean, got " + i);
        }
    }

    private static String longName(Object o) {
        if (!(o instanceof Long)) return o.toString();
        long l = (Long) o;
        if (l == Long.MAX_VALUE) return "Long.MAX_VALUE";
        if (l == Long.MIN_VALUE) return "Long.MIN_VALUE";
        if (l == Integer.MAX_VALUE) return "Integer.MAX_VALUE";
        if (l == Integer.MIN_VALUE) return "Integer.MIN_VALUE";
        return o.toString();
    }

    @Override
    public String toString() {
        switch (type) {
            case String:
                return "\"" + value + "\"";
            case NullObject:
                return "null";
            case Integer:
                switch (inferredJavaType.getRawType()) {
                    case CHAR:
                        return charName(value);
                    case BOOLEAN:
                        return boolName(value);
                    default:
                        return integerName(value);
                }
            case Long:
                return longName(value);
            default:
                return value.toString();
        }
    }

    public static TypedLiteral getLong(long v) {
        return new TypedLiteral(LiteralType.Long, new InferredJavaType(RawJavaType.LONG, InferredJavaType.Source.LITERAL), v);
    }

    public static TypedLiteral getInt(int v) {
        return new TypedLiteral(LiteralType.Integer, new InferredJavaType(RawJavaType.INT, InferredJavaType.Source.LITERAL), v);
    }

    public static TypedLiteral getDouble(double v) {
        return new TypedLiteral(LiteralType.Double, new InferredJavaType(RawJavaType.DOUBLE, InferredJavaType.Source.LITERAL), v);
    }

    public static TypedLiteral getFloat(float v) {
        return new TypedLiteral(LiteralType.Double, new InferredJavaType(RawJavaType.FLOAT, InferredJavaType.Source.LITERAL), v);
    }

    public static TypedLiteral getClass(String v) {
        return new TypedLiteral(LiteralType.Class, new InferredJavaType(RawJavaType.REF, InferredJavaType.Source.LITERAL), v);
    }

    public static TypedLiteral getString(String v) {
        return new TypedLiteral(LiteralType.String, new InferredJavaType(RawJavaType.REF, InferredJavaType.Source.LITERAL), v);
    }

    public static TypedLiteral getNull() {
        return new TypedLiteral(LiteralType.NullObject, new InferredJavaType(RawJavaType.REF, InferredJavaType.Source.LITERAL), null);
    }

    public static TypedLiteral getConstantPoolEntry(ConstantPool cp, ConstantPoolEntry cpe) {
        if (cpe instanceof ConstantPoolEntryDouble) {
            return getDouble(((ConstantPoolEntryDouble) cpe).getValue());
        } else if (cpe instanceof ConstantPoolEntryFloat) {
            return getFloat(((ConstantPoolEntryFloat) cpe).getValue());
        } else if (cpe instanceof ConstantPoolEntryLong) {
            return getLong(((ConstantPoolEntryLong) cpe).getValue());
        } else if (cpe instanceof ConstantPoolEntryInteger) {
            return getInt(((ConstantPoolEntryInteger) cpe).getValue());
        } else if (cpe instanceof ConstantPoolEntryString) {
            return getString(((ConstantPoolEntryString) cpe).getValue(cp));
        } else if (cpe instanceof ConstantPoolEntryClass) {
            return getClass(cp.getUTF8Entry(((ConstantPoolEntryClass) cpe).getNameIndex()).getValue());
        }
        throw new ConfusedCFRException("Can't turn ConstantPoolEntry into Literal - got " + cpe);
    }

    public LiteralType getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public InferredJavaType getInferredJavaType() {
        return inferredJavaType;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof TypedLiteral)) return false;
        TypedLiteral other = (TypedLiteral) o;
        return type == other.type && value.equals(other.value);
    }
}
