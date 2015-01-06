package org.benf.cfr.reader.bytecode.analysis.parse.literal;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.QuotingUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.constantpool.*;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

public class TypedLiteral implements TypeUsageCollectable, Dumpable {

    public enum LiteralType {
        Integer,
        Long,
        Double,
        Float,
        String,
        NullObject,
        Class,
        MethodHandle,  // Only used for invokedynamic arguments
        MethodType     // Only used for invokedynamic arguments
    }

    private final InferredJavaType inferredJavaType;
    private final LiteralType type;
    private final Object value;

    protected TypedLiteral(LiteralType type, InferredJavaType inferredJavaType, Object value) {
        this.type = type;
        this.value = value;
        this.inferredJavaType = inferredJavaType;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        if (type == LiteralType.Class) {
            collector.collect((JavaTypeInstance) value);
        }
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

    public boolean getBoolValue() {
        if (type != LiteralType.Integer) throw new IllegalStateException("Expecting integral literal");
        Integer i = (Integer) value;
        return (i != 0);
    }

    public Boolean getMaybeBoolValue() {
        if (type != LiteralType.Integer) return null;
        Integer i = (Integer) value;
        return (i == 0) ? Boolean.FALSE : Boolean.TRUE;
    }

    // fixme - move into QuotingUtils.
    private static String charName(Object o) {
        if (!(o instanceof Integer)) throw new ConfusedCFRException("Expecting char-as-int");
        int i = (Integer) o;
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
            case '\b':
                return "'\\b'";
            case '\f':
                return "'\\f'";
            case '\\':
                return "'\\\\'";
            case '\'':
                return "'\\\''";
            default:
                if (i < 32 || i >= 254) {
                    // perversely, java will allow you to compare non-char values to chars
                    // happily..... (also pretty print for out of range.)
                    return "'\\u" + String.format("%04x", i) + "\'";
                } else {
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
        String longString = o.toString();
        if (l > 0xfffffL) {
            String hexTest = Long.toHexString(l).toUpperCase();
            byte[] bytes = hexTest.getBytes();
            byte[] count = new byte[16];
            int diff = 0;
            for (int i = 0, len = bytes.length; i < len; ++i) {
                byte b = bytes[i];
                if (b >= '0' && b <= '9') {
                    if (++count[bytes[i] - '0'] == 1) diff++;
                } else if (b >= 'A' && b <= 'F') {
                    if (++count[bytes[i] - 'A' + 10] == 1) diff++;
                } else {
                    diff = 10;
                    break;
                }
            }
            if (diff <= 2) longString = "0x" + hexTest;
        }

        if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) {
            return longString + "L";
        } else {
            return longString;
        }
    }

    private static String methodHandleName(Object o) {
        ConstantPoolEntryMethodHandle methodHandle = (ConstantPoolEntryMethodHandle) o;
        ConstantPoolEntryMethodRef methodRef = methodHandle.getMethodRef();
        return methodRef.getMethodPrototype().toString();
    }

    private static String methodTypeName(Object o) {
        ConstantPoolEntryMethodType methodType = (ConstantPoolEntryMethodType) o;
        return methodType.getDescriptor().getValue();
    }

    @Override
    public Dumper dump(Dumper d) {
        switch (type) {
            case String:
                return d.print(((String) value));
            case NullObject:
                return d.print("null");
            case Integer:
                switch (inferredJavaType.getRawType()) {
                    case CHAR:
                        return d.print(charName(value));
                    case BOOLEAN:
                        return d.print(boolName(value));
                    default:
                        return d.print(integerName(value));
                }
            case Long:
                return d.print(longName(value));
            case MethodType:
                return d.print(methodTypeName(value));
            case MethodHandle:
                return d.print(methodHandleName(value));
            case Class:
                return d.dump((JavaTypeInstance) value).print(".class");
            case Float:
                return d.print(value.toString()).print("f");
            default:
                return d.print(value.toString());
        }
    }

    @Override
    public String toString() {
        return ToStringDumper.toString(this);
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
        return new TypedLiteral(LiteralType.Float, new InferredJavaType(RawJavaType.FLOAT, InferredJavaType.Source.LITERAL), v);
    }

    public static TypedLiteral getClass(JavaTypeInstance v) {
        return new TypedLiteral(LiteralType.Class, new InferredJavaType(RawJavaType.REF, InferredJavaType.Source.LITERAL), v);
    }

    public static TypedLiteral getString(String v) {
        return new TypedLiteral(LiteralType.String, new InferredJavaType(TypeConstants.STRING, InferredJavaType.Source.LITERAL), v);
    }

    public static TypedLiteral getNull() {
        return new TypedLiteral(LiteralType.NullObject, new InferredJavaType(RawJavaType.NULL, InferredJavaType.Source.LITERAL), null);
    }

    public static TypedLiteral getMethodHandle(ConstantPoolEntryMethodHandle methodHandle, ConstantPool cp) {
        JavaTypeInstance typeInstance = cp.getClassCache().getRefClassFor("java.lang.invoke.MethodHandle");
        return new TypedLiteral(LiteralType.MethodHandle, new InferredJavaType(typeInstance, InferredJavaType.Source.LITERAL), methodHandle);
    }

    public static TypedLiteral getMethodType(ConstantPoolEntryMethodType methodType, ConstantPool cp) {
//        ConstantPoolEntryUTF8 descriptor = methodType.getDescriptor();
        JavaTypeInstance typeInstance = cp.getClassCache().getRefClassFor("java.lang.invoke.MethodType");
        return new TypedLiteral(LiteralType.MethodType, new InferredJavaType(typeInstance, InferredJavaType.Source.LITERAL), methodType);
    }

    public static TypedLiteral getConstantPoolEntryUTF8(ConstantPoolEntryUTF8 cpe) {
        return getString(QuotingUtils.enquoteString(cpe.getValue()));
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
            return getClass(((ConstantPoolEntryClass) cpe).getTypeInstance());
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
        return type == other.type && (value == null ? other.value == null : value.equals(other.value));
    }

}
