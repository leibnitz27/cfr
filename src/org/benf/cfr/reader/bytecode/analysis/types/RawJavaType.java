package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 06:57
 */
public enum RawJavaType implements JavaTypeInstance {
    BOOLEAN("boolean", StackType.INT, true, TypeConstants.boxingNameBoolean),
    BYTE("byte", StackType.INT, true, TypeConstants.boxingNameByte),
    CHAR("char", StackType.INT, true, TypeConstants.boxingNameChar),
    SHORT("short", StackType.INT, true, TypeConstants.boxingNameShort),
    INT("int", StackType.INT, true, TypeConstants.boxingNameInt),
    LONG("long", StackType.LONG, true, TypeConstants.boxingNameLong),
    FLOAT("float", StackType.FLOAT, true, TypeConstants.boxingNameFloat),
    DOUBLE("double", StackType.DOUBLE, true, TypeConstants.boxingNameDouble),
    VOID("void", StackType.VOID, false),
    REF("reference", StackType.REF, false),  // Don't use for fixedtypeinstance.
    RETURNADDRESS("returnaddress", StackType.RETURNADDRESS, false),
    RETURNADDRESSORREF("returnaddress or ref", StackType.RETURNADDRESSORREF, false),
    NULL("null", StackType.REF, false);  // Null is a special type, sort of.

    private final String name;
    private final StackType stackType;
    private final boolean usableType;
    private final String boxedName;

    private static final Map<RawJavaType, Set<RawJavaType>> implicitCasts = MapFactory.newMap();
    private static final Map<String, RawJavaType> boxingTypes = MapFactory.newMap();

    static {
        implicitCasts.put(FLOAT, SetFactory.newSet(DOUBLE));
        implicitCasts.put(LONG, SetFactory.newSet(FLOAT, DOUBLE));
        implicitCasts.put(INT, SetFactory.newSet(LONG, FLOAT, DOUBLE));
        implicitCasts.put(CHAR, SetFactory.newSet(INT, LONG, FLOAT, DOUBLE));
        implicitCasts.put(SHORT, SetFactory.newSet(INT, LONG, FLOAT, DOUBLE));
        implicitCasts.put(BYTE, SetFactory.newSet(SHORT, INT, LONG, FLOAT, DOUBLE));
        for (RawJavaType type : values()) {
            if (type.boxedName != null) {
                boxingTypes.put(type.boxedName, type);
            }
        }
    }

    public static RawJavaType getUnboxedTypeFor(JavaRefTypeInstance type) {
        String rawName = type.getRawName();
        RawJavaType tgt = boxingTypes.get(rawName);
        return tgt;
    }


    private RawJavaType(String name, StackType stackType, boolean usableType, String boxedName) {
        this.name = name;
        this.stackType = stackType;
        this.usableType = usableType;
        this.boxedName = boxedName;
    }

    private RawJavaType(String name, StackType stackType, boolean usableType) {
        this(name, stackType, usableType, null);
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

    public int compareAllPriorityTo(RawJavaType other) {
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
    public InnerClassInfo getInnerClassHereInfo() {
        return InnerClassInfo.NOT;
    }

    @Override
    public BindingSuperContainer getBindingSupers() {
        return null;
    }

    private boolean implicitlyCastsTo(RawJavaType other) {
        if (other == this) return true;
        Set<RawJavaType> tgt = implicitCasts.get(this);
        if (tgt == null) return false;
        return tgt.contains(other);
    }

    /* Obey the exact specficiation from 5.1.2 JLS */
    @Override
    public boolean implicitlyCastsTo(JavaTypeInstance other) {
        if (other instanceof RawJavaType) {
            return implicitlyCastsTo((RawJavaType) other);
        }
        if (this == RawJavaType.NULL) return true;
        if (this == RawJavaType.REF) return true;
        /*
         * handle boxing.
         */
        if (other instanceof JavaRefTypeInstance) {
            RawJavaType tgt = getUnboxedTypeFor((JavaRefTypeInstance) other);
            if (tgt == null) return false;
            return implicitlyCastsTo(tgt);
        }
        return false;
    }

    @Override
    public boolean canCastTo(JavaTypeInstance other) {
        if (this.boxedName != null && other instanceof JavaRefTypeInstance) {
            // Can only cast directly to the 'correct' type.
            return other.canCastTo(this);
        }
        return true;
    }


    public String getCastString() {
        return name;
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
