package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 06:57
 */
public enum RawJavaType implements JavaTypeInstance {
    BOOLEAN("boolean", StackType.INT),
    BYTE("byte", StackType.INT),
    CHAR("char", StackType.INT),
    SHORT("short", StackType.INT),
    INT("int", StackType.INT),
    FLOAT("float", StackType.FLOAT),
    REF("reference", StackType.REF),  // Don't use for fixedtypeinstance.
    RETURNADDRESS("returnaddress", StackType.RETURNADDRESS),
    RETURNADDRESSORREF("returnaddress or ref", StackType.RETURNADDRESSORREF),
    LONG("long", StackType.LONG),
    DOUBLE("double", StackType.DOUBLE),
    VOID("void", StackType.VOID),
    NULL("null", StackType.REF);  // Null is a special type, sort of.

    private final String name;
    private final StackType stackType;

    private RawJavaType(String name, StackType stackType) {
        this.name = name;
        this.stackType = stackType;
    }

    public String getName() {
        return name;
    }

    @Override
    public StackType getStackType() {
        return stackType;
    }

    @Override
    public String toString() {
        return name;
    }

    public static RawJavaType getMaximalJavaTypeForStackType(StackType stackType) {
        switch (stackType) {
            case INT:
                return RawJavaType.INT;
            case FLOAT:
                return RawJavaType.FLOAT;
            case REF:
                return RawJavaType.REF;
            case RETURNADDRESS:
                return RawJavaType.RETURNADDRESS;
            case RETURNADDRESSORREF:
                return RawJavaType.RETURNADDRESSORREF;
            case LONG:
                return RawJavaType.LONG;
            case DOUBLE:
                return RawJavaType.DOUBLE;
            default:
                throw new ConfusedCFRException("Unexpected stacktype.");
        }
    }
}
