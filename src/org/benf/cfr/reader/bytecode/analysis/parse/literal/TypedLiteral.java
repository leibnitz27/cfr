package org.benf.cfr.reader.bytecode.analysis.parse.literal;

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

    enum LiteralType {
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

    @Override
    public String toString() {
        switch (type) {
            case String:
                return "\"" + value + "\"";
            case NullObject:
                return "null";
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
}
