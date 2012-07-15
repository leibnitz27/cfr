package org.benf.cfr.reader.bytecode.analysis.parse.literal;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.KnownJavaType;
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
        Class
    }

    private final LiteralType type;
    private final Object value;

    protected TypedLiteral(LiteralType type, Object value) {
        this.type = type;
        this.value = value;
    }

    /*
     * this is the javatype which is the most encompassing - i.e. if we have a ' ' in the bytecode,
     * it will be an int 32.  Hence we return Unknown type.
     */
    public KnownJavaType getKnownJavaType() {
        switch (type) {
            case Integer:
                return KnownJavaType.getUnknownJavaType(JavaType.INT);
            case Long:
                return KnownJavaType.getUnknownJavaType(JavaType.LONG);
            case Double:
                return KnownJavaType.getUnknownJavaType(JavaType.DOUBLE);
            case String:
                return KnownJavaType.getKnownJavaType(new JavaRefTypeInstance(TypeConstants.JAVA_STRING_CLASS));
            case NullObject:
                return KnownJavaType.getUnknownJavaType(JavaType.NULL);
            case Class:
                return KnownJavaType.getKnownJavaType(new JavaRefTypeInstance((String) value));
            default:
                throw new IllegalStateException("Bad type");
        }
    }

    private static String IntegerName(Object o) {
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

    private static String LongName(Object o) {
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
                return IntegerName(value);
            case Long:
                return LongName(value);
            default:
                return value.toString();
        }
    }

    public static TypedLiteral getLong(long v) {
        return new TypedLiteral(LiteralType.Long, v);
    }

    public static TypedLiteral getInt(int v) {
        return new TypedLiteral(LiteralType.Integer, v);
    }

    public static TypedLiteral getDouble(double v) {
        return new TypedLiteral(LiteralType.Double, v);
    }

    public static TypedLiteral getNull() {
        return new TypedLiteral(LiteralType.NullObject, null);
    }

    public static TypedLiteral getConstantPoolEntry(ConstantPool cp, ConstantPoolEntry cpe) {
        if (cpe instanceof ConstantPoolEntryDouble) {
            return new TypedLiteral(LiteralType.Double, ((ConstantPoolEntryDouble) cpe).getValue());
        } else if (cpe instanceof ConstantPoolEntryFloat) {
            return new TypedLiteral(LiteralType.Double, ((ConstantPoolEntryFloat) cpe).getValue());
        } else if (cpe instanceof ConstantPoolEntryLong) {
            return new TypedLiteral(LiteralType.Long, ((ConstantPoolEntryLong) cpe).getValue());
        } else if (cpe instanceof ConstantPoolEntryInteger) {
            return new TypedLiteral(LiteralType.Integer, ((ConstantPoolEntryInteger) cpe).getValue());
        } else if (cpe instanceof ConstantPoolEntryString) {
            return new TypedLiteral(LiteralType.String, ((ConstantPoolEntryString) cpe).getValue(cp));
        } else if (cpe instanceof ConstantPoolEntryClass) {
            return new TypedLiteral(LiteralType.Class, cp.getUTF8Entry(((ConstantPoolEntryClass) cpe).getNameIndex()).getValue());
        }
        throw new ConfusedCFRException("Can't turn ConstantPoolEntry into Literal - got " + cpe);
    }

    public LiteralType getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }
}
