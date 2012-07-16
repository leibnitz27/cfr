package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.util.ConfusedCFRException;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 06:57
 */
public enum JavaType implements JavaTypeInstance {
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

    private JavaType(String name, StackType stackType) {
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

    public static JavaType getJavaTypeForStackType(StackType stackType) {
        switch (stackType) {
            case INT:
                return JavaType.INT;
            case FLOAT:
                return JavaType.FLOAT;
            case REF:
                return JavaType.REF;
            case RETURNADDRESS:
                return JavaType.RETURNADDRESS;
            case RETURNADDRESSORREF:
                return JavaType.RETURNADDRESSORREF;
            case LONG:
                return JavaType.LONG;
            case DOUBLE:
                return JavaType.DOUBLE;
            default:
                throw new ConfusedCFRException("Unexpected stacktype.");
        }
    }
}
