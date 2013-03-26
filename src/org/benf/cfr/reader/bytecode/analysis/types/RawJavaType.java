package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 06:57
 */
public enum RawJavaType implements JavaTypeInstance {
    BOOLEAN("boolean", StackType.INT, true),
    BYTE("byte", StackType.INT, true),
    CHAR("char", StackType.INT, true),
    SHORT("short", StackType.INT, true),
    INT("int", StackType.INT, true),
    FLOAT("float", StackType.FLOAT, true),
    REF("reference", StackType.REF, false),  // Don't use for fixedtypeinstance.
    RETURNADDRESS("returnaddress", StackType.RETURNADDRESS, false),
    RETURNADDRESSORREF("returnaddress or ref", StackType.RETURNADDRESSORREF, false),
    LONG("long", StackType.LONG, true),
    DOUBLE("double", StackType.DOUBLE, true),
    VOID("void", StackType.VOID, false),
    NULL("null", StackType.REF, false);  // Null is a special type, sort of.

    private final String name;
    private final StackType stackType;
    private final boolean usableType;

    private RawJavaType(String name, StackType stackType, boolean usableType) {
        this.name = name;
        this.stackType = stackType;
        this.usableType = usableType;
    }

    public String getName() {
        return name;
    }

    @Override
    public StackType getStackType() {
        return stackType;
    }

    @Override
    public boolean isComplexType() {
        return false;
    }

    /*
     * Compare integral type priorities.
     *
     * Int, Bool -> -ve
     * Bool, Int -> +ve
     */
    public int compareTypePriorityTo(RawJavaType other) {
        if (stackType != StackType.INT) throw new IllegalArgumentException();
        if (other.stackType != StackType.INT) throw new IllegalArgumentException();
        return this.ordinal() - other.ordinal();
    }

    @Override
    public boolean isUsableType() {
        return usableType;
    }

    @Override
    public RawJavaType getRawTypeOfSimpleType() {
        return this;
    }

    @Override
    public JavaTypeInstance removeAnArrayIndirection() {
        return VOID;
    }

    @Override
    public JavaTypeInstance getArrayStrippedType() {
        return this;
    }

    @Override
    public JavaTypeInstance getDeGenerifiedType() {
        return this;
    }


    @Override
    public int getNumArrayDimensions() {
        return 0;
    }

    @Override
    public String getRawName() {
        return name;
    }

    @Override
    public boolean isInnerClassOf(JavaTypeInstance possibleParent) {
        return false;
    }

    @Override
    public boolean isInnerClass() {
        return false;
    }


    public String getCastString() {
        return "(" + name + ")";
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

    public static Map<String, RawJavaType> rawJavaTypeMap;

    public static RawJavaType getByName(String name) {
        if (rawJavaTypeMap == null) {
            rawJavaTypeMap = MapFactory.newMap();
            for (RawJavaType typ : RawJavaType.values()) {
                rawJavaTypeMap.put(typ.getName(), typ);
            }
        }
        RawJavaType res = rawJavaTypeMap.get(name);
        if (res == null) {
            throw new ConfusedCFRException("No RawJavaType '" + name + "'");
        }
        return res;
    }
}
