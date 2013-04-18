package org.benf.cfr.reader.bytecode.analysis.parse.literal;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
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
        Class,
        MethodHandle,  // Only used for invokedynamic arguments
        MethodType;    // Only used for invokedynamic arguments
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
        if (i < 32 || i >= 254) {
            // perversely, java will allow you to compare non-char values to chars
            // happily..... (also pretty print for out of range.)
            return Integer.toString(i);
        } else {
            char c = (char) i;
            switch (c) {
                case '\"':
                    return "'\\\"'";
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
                return "BADBOOL " + i;
//                throw new ConfusedCFRException("Expecting a boolean, got " + i);
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

    private static String methodHandleName(Object o) {
        ConstantPoolEntryMethodHandle methodHandle = (ConstantPoolEntryMethodHandle) o;
        ConstantPoolEntryMethodRef methodRef = methodHandle.getMethodRef();
        return methodRef.getMethodPrototype().toString();
    }

    private static String methodTypeName(Object o) {
        ConstantPoolEntryUTF8 methodTypeString = (ConstantPoolEntryUTF8) o;
        return methodTypeString.toString();
    }

    @Override
    public String toString() {
        switch (type) {
            case String:
                return enQuote((String) value);
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
            case MethodType:
                return methodTypeName(value);
            case MethodHandle:
                return methodHandleName(value);
            case Class:
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

    // We don't know that a literal 1 or 0 is an integer, short or boolean.
    // We always guess at boolean, that way if we're proved wrong we can easily
    // promote the type to integer.
    public static TypedLiteral getBoolean(int v) {
        return new TypedLiteral(LiteralType.Integer, new InferredJavaType(RawJavaType.BOOLEAN, InferredJavaType.Source.LITERAL), v);
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

    public static TypedLiteral getMethodHandle(ConstantPoolEntryMethodHandle methodHandle, ConstantPool cp) {
        JavaTypeInstance typeInstance = cp.getClassCache().getRefClassFor("java.lang.invoke.MethodHandle");
        return new TypedLiteral(LiteralType.MethodHandle, new InferredJavaType(typeInstance, InferredJavaType.Source.LITERAL), methodHandle);
    }

    public static TypedLiteral getMethodType(ConstantPoolEntryMethodType methodType, ConstantPool cp) {
        ConstantPoolEntryUTF8 descriptor = cp.getUTF8Entry(methodType.getDescriptorIndex());
        JavaTypeInstance typeInstance = cp.getClassCache().getRefClassFor("java.lang.invoke.MethodType");
        return new TypedLiteral(LiteralType.MethodType, new InferredJavaType(typeInstance, InferredJavaType.Source.LITERAL), descriptor);
    }

    // TODO : Quote strings properly
    private static String enQuote(String in) {
        return '\"' + in.replaceAll("\"", "\\\\\"") + '\"';
    }

    public static TypedLiteral getConstantPoolEntryUTF8(ConstantPool cp, ConstantPoolEntryUTF8 cpe) {
        return getString(cpe.getValue());
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
            return getString(((ConstantPoolEntryString) cpe).getValue());
        } else if (cpe instanceof ConstantPoolEntryClass) {
            return getClass(((ConstantPoolEntryClass) cpe).getTextName());
        } else if (cpe instanceof ConstantPoolEntryMethodHandle) {
            return getMethodHandle((ConstantPoolEntryMethodHandle) cpe, cp);
        } else if (cpe instanceof ConstantPoolEntryMethodType) {
            return getMethodType((ConstantPoolEntryMethodType) cpe, cp);
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
